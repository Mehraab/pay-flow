import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Pagination } from './pagination';

describe('Pagination', () => {
  let fixture: ComponentFixture<Pagination>;
  let component: Pagination;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Pagination] });
    fixture = TestBed.createComponent(Pagination);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('currentPage', 2);
    fixture.componentRef.setInput('totalPages', 10);
    fixture.detectChanges();
  });

  it('emits pageChange when a different, in-range page is clicked', () => {
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    (component as any).goTo(5);

    expect(emitted).toEqual([5]);
  });

  it('does not emit when the clicked page is already the current page', () => {
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    (component as any).goTo(2);

    expect(emitted).toEqual([]);
  });

  it('does not emit for an out-of-range page (negative or >= totalPages)', () => {
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    (component as any).goTo(-1);
    (component as any).goTo(10);

    expect(emitted).toEqual([]);
  });

  it('submitJump() converts the 1-indexed typed value to a 0-indexed pageChange', () => {
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    (component as any).onJumpInput('7');
    (component as any).submitJump();

    expect(emitted).toEqual([6]);
  });

  it('submitJump() does nothing for a non-numeric or empty value', () => {
    const emitted: number[] = [];
    component.pageChange.subscribe((p) => emitted.push(p));

    (component as any).onJumpInput('');
    (component as any).submitJump();
    (component as any).onJumpInput('abc');
    (component as any).submitJump();

    expect(emitted).toEqual([]);
  });

  it('isAdjacent() is true only for currentPage ± 1', () => {
    expect((component as any).isAdjacent(1)).toBe(true);
    expect((component as any).isAdjacent(3)).toBe(true);
    expect((component as any).isAdjacent(2)).toBe(false);
    expect((component as any).isAdjacent(0)).toBe(false);
  });
});
