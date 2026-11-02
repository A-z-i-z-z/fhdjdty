import Mathlib

open Finset

theorem o_ab_bc_ca_le (a b c : ℝ) (ha : 0 ≤ a) (hb : 0 ≤ b) (hc : 0 ≤ c) (h : a + b + c = 1) : a * b + b * c + c * a - 2 * a * b * c ≤ 7 / 27 := by
  have key : (a + b + c) ^ 3 - 4 * (a + b + c) * (a * b + b * c + c * a) + 9 * (a * b * c) ≥ 0 := by
    rcases le_total a b with hab | hab <;> rcases le_total b c with hbc | hbc <;>
      rcases le_total a c with hac | hac <;>
      nlinarith [mul_nonneg (mul_nonneg ha hb) hc,
        mul_nonneg ha (sq_nonneg (a - b)), mul_nonneg ha (sq_nonneg (b - c)), mul_nonneg ha (sq_nonneg (c - a)),
        mul_nonneg hb (sq_nonneg (a - b)), mul_nonneg hb (sq_nonneg (b - c)), mul_nonneg hb (sq_nonneg (c - a)),
        mul_nonneg hc (sq_nonneg (a - b)), mul_nonneg hc (sq_nonneg (b - c)), mul_nonneg hc (sq_nonneg (c - a)),
        mul_nonneg (sq_nonneg (a - b)) (sub_nonneg.2 hab), mul_nonneg (sq_nonneg (a - b)) (sub_nonneg.2 hbc),
        mul_nonneg (sq_nonneg (a - b)) (sub_nonneg.2 hac),
        mul_nonneg (sq_nonneg (b - c)) (sub_nonneg.2 hab), mul_nonneg (sq_nonneg (b - c)) (sub_nonneg.2 hbc),
        mul_nonneg (sq_nonneg (b - c)) (sub_nonneg.2 hac),
        mul_nonneg (sq_nonneg (c - a)) (sub_nonneg.2 hab), mul_nonneg (sq_nonneg (c - a)) (sub_nonneg.2 hbc),
        mul_nonneg (sq_nonneg (c - a)) (sub_nonneg.2 hac),
        mul_nonneg (mul_nonneg ha (sub_nonneg.2 hab)) (sub_nonneg.2 hbc),
        mul_nonneg (mul_nonneg hb (sub_nonneg.2 hab)) (sub_nonneg.2 hbc),
        mul_nonneg (mul_nonneg hc (sub_nonneg.2 hab)) (sub_nonneg.2 hbc),
        mul_nonneg (mul_nonneg ha (sub_nonneg.2 hbc)) (sub_nonneg.2 hac),
        mul_nonneg (mul_nonneg hb (sub_nonneg.2 hbc)) (sub_nonneg.2 hac),
        mul_nonneg (mul_nonneg hc (sub_nonneg.2 hbc)) (sub_nonneg.2 hac),
        mul_nonneg (mul_nonneg ha (sub_nonneg.2 hab)) (sub_nonneg.2 hac),
        mul_nonneg (mul_nonneg hb (sub_nonneg.2 hab)) (sub_nonneg.2 hac),
        mul_nonneg (mul_nonneg hc (sub_nonneg.2 hab)) (sub_nonneg.2 hac)]
  rw [h] at key
  have h2 : (a + b + c) ^ 2 = 1 := by
    rw [h]
    norm_num
  have hq : a * b + b * c + c * a ≤ 1 / 3 := by
    nlinarith [sq_nonneg (a - b), sq_nonneg (b - c), sq_nonneg (c - a)]
  nlinarith [key, hq]

#print axioms o_ab_bc_ca_le
