import Mathlib

open Finset

theorem ml_cyclic_ratio_aux (a b c : ℝ) (ha : 0 < a) (hb : 0 < b) (hc : 0 < c) :
    3 * (a * b * c) ≤ a ^ 2 * c + a * b ^ 2 + b * c ^ 2 := by
  rcases le_total a b with h1 | h1 <;> rcases le_total b c with h2 | h2 <;>
    rcases le_total c a with h3 | h3 <;>
    nlinarith [mul_nonneg hc.le (sq_nonneg (a - b)),
      mul_nonneg ha.le (sq_nonneg (b - c)),
      mul_nonneg hb.le (sq_nonneg (c - a)),
      mul_nonneg hb.le (mul_nonneg (sub_nonneg.2 h3) (sub_nonneg.2 h2)),
      mul_nonneg hc.le (mul_nonneg (sub_nonneg.2 h1) (sub_nonneg.2 h3)),
      mul_nonneg ha.le (mul_nonneg (sub_nonneg.2 h2) (sub_nonneg.2 h1))]

theorem ml_cyclic_ratio (a b c : ℝ) (ha : 0 < a) (hb : 0 < b) (hc : 0 < c) : 3 ≤ a / b + b / c + c / a := by
  have key := ml_cyclic_ratio_aux a b c ha hb hc
  have h : a / b + b / c + c / a = (a ^ 2 * c + a * b ^ 2 + b * c ^ 2) / (a * b * c) := by
    rw [div_add_div _ _ hb.ne' hc.ne', div_add_div _ _ (mul_ne_zero hb.ne' hc.ne') ha.ne',
      div_eq_div_iff (by positivity) (by positivity)]
    ring
  rw [h, le_div_iff₀ (by positivity)]
  linarith

#print axioms ml_cyclic_ratio
