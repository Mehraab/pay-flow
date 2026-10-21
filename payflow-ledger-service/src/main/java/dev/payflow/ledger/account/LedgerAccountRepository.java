package dev.payflow.ledger.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerAccountRepository {

  private final NamedParameterJdbcTemplate jdbc;

  public LedgerAccountRepository(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Matched by exact display name OR by a kebab-case slug of it (e.g. {@code "platform-bank"}
   * matches {@code "Platform Bank"}), so callers don't need to know the exact display spelling.
   */
  private static final String NAME_OR_SLUG_MATCH =
      "(LOWER(REPLACE(account_name, ' ', '-')) = LOWER(:name) OR account_name = :name)";

  public Optional<LedgerAccount> findPlatformAccount(String accountName) {
    List<LedgerAccount> rows =
        jdbc.query(
            "SELECT * FROM ledger_accounts WHERE merchant_id IS NULL AND " + NAME_OR_SLUG_MATCH,
            new MapSqlParameterSource("name", accountName),
            this::mapRow);
    return rows.stream().findFirst();
  }

  public Optional<LedgerAccount> findMerchantAccount(UUID merchantId, String accountName) {
    List<LedgerAccount> rows =
        jdbc.query(
            "SELECT * FROM ledger_accounts WHERE merchant_id = :merchantId AND " + NAME_OR_SLUG_MATCH,
            new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("name", accountName),
            this::mapRow);
    return rows.stream().findFirst();
  }

  /** Every merchant-scoped account (across all merchants) matching this name or slug. */
  public List<LedgerAccount> findMerchantAccountsByNameOrSlug(String accountName) {
    return jdbc.query(
        "SELECT * FROM ledger_accounts WHERE merchant_id IS NOT NULL AND " + NAME_OR_SLUG_MATCH,
        new MapSqlParameterSource("name", accountName),
        this::mapRow);
  }

  /** Every merchant currently holding an available (uncollected) balance — feeds the EOD sweep. */
  public List<LedgerAccount> findMerchantPayableAccountsWithPositiveBalance() {
    return jdbc.query(
        """
        SELECT * FROM ledger_accounts
        WHERE merchant_id IS NOT NULL AND account_name = :name AND balance_minor > 0
        """,
        new MapSqlParameterSource("name", LedgerAccountService.MERCHANT_PAYABLE),
        this::mapRow);
  }

  public Optional<LedgerAccount> findById(UUID accountId) {
    List<LedgerAccount> rows =
        jdbc.query(
            "SELECT * FROM ledger_accounts WHERE id = :id",
            new MapSqlParameterSource("id", accountId),
            this::mapRow);
    return rows.stream().findFirst();
  }

  /**
   * Plain insert, no conflict handling — a Postgres UNIQUE constraint never fires for two rows
   * that both have {@code merchant_id IS NULL}, since NULLs are never equal to each other, so
   * {@code ON CONFLICT} on this column pair is meaningless for platform accounts. Callers must
   * check {@link #findPlatformAccount} first; platform accounts are only ever seeded once, at
   * startup, so this doesn't need to be race-safe the way merchant account creation does.
   */
  public LedgerAccount insertPlatformAccount(
      AccountType type, String accountName, NormalBalance normalBalance) {
    return jdbc.queryForObject(
        """
        INSERT INTO ledger_accounts (merchant_id, account_type, account_name, normal_balance)
        VALUES (NULL, :type, :name, :normalBalance)
        RETURNING *
        """,
        new MapSqlParameterSource()
            .addValue("type", type.name())
            .addValue("name", accountName)
            .addValue("normalBalance", normalBalance.name()),
        this::mapRow);
  }

  /**
   * {@code merchant_id} is never null here, so the {@code (merchant_id, account_name)} UNIQUE
   * constraint is real — {@code ON CONFLICT DO NOTHING} safely resolves a race between two
   * concurrent first-transactions for the same new merchant. Returns empty when another
   * transaction won the race; the caller falls back to {@link #findMerchantAccount}.
   */
  public Optional<LedgerAccount> insertMerchantAccountIfAbsent(
      UUID merchantId, AccountType type, String accountName, NormalBalance normalBalance) {
    List<LedgerAccount> rows =
        jdbc.query(
            """
            INSERT INTO ledger_accounts (merchant_id, account_type, account_name, normal_balance)
            VALUES (:merchantId, :type, :name, :normalBalance)
            ON CONFLICT (merchant_id, account_name) DO NOTHING
            RETURNING *
            """,
            new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("type", type.name())
                .addValue("name", accountName)
                .addValue("normalBalance", normalBalance.name()),
            this::mapRow);
    return rows.stream().findFirst();
  }

  public void adjustBalance(UUID accountId, long deltaMinor) {
    jdbc.update(
        "UPDATE ledger_accounts SET balance_minor = balance_minor + :delta WHERE id = :id",
        new MapSqlParameterSource().addValue("delta", deltaMinor).addValue("id", accountId));
  }

  private LedgerAccount mapRow(ResultSet rs, int rowNum) throws SQLException {
    String merchantIdStr = rs.getString("merchant_id");
    return new LedgerAccount(
        UUID.fromString(rs.getString("id")),
        merchantIdStr == null ? null : UUID.fromString(merchantIdStr),
        AccountType.valueOf(rs.getString("account_type")),
        rs.getString("account_name"),
        NormalBalance.valueOf(rs.getString("normal_balance")),
        rs.getLong("balance_minor"));
  }
}
