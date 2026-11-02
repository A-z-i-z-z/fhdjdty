import Mathlib

open Finset

lemma s_recip_sq_sum_aux (a b u v : ℝ) (ha : 0 < a) (hb : 0 < b) (h : a + b = 1)
    (hu : a * u = 1) (hv : b * v = 1) (hu0 : 0 < u) (hv0 : 0 < v) :
    25 / 2 ≤ (a + u) ^ 2 + (b + v) ^ 2 := by
  have hs : u + v = u * v := by
    linear_combination (-u) * hv + (-v) * hu + (u * v) * h
  have hp : a * b * (u * v) = 1 := by
    linear_combination (b * v) * hu + hv
  have hab : a * b ≤ 1 / 4 := by
    have hb1 : b = 1 - a := by linarith
    rw [hb1]
    nlinarith [sq_nonneg (2 * a - 1)]
  have hs0 : 0 < u * v := mul_pos hu0 hv0
  have hs4 : 4 ≤ u * v := by
    nlinarith [mul_nonneg (sub_nonneg.mpr hab) hs0.le]
  have hK : 0 ≤ (u * v - 4) * ((u * v) ^ 2 + 2 * (u * v) + 1 / 2) := by
    apply mul_nonneg
    · linarith
    · nlinarith [hs0, sq_nonneg (u * v)]
  have hX : 0 ≤ (1 - 2 * (a * b) + ((u * v) ^ 2 - 2 * (u * v)) - 17 / 2) * (u * v) := by
    nlinarith [hp, hK]
  have hX' : 0 ≤ 1 - 2 * (a * b) + ((u * v) ^ 2 - 2 * (u * v)) - 17 / 2 := by
    nlinarith [hX, hs0]
  have hsq : a ^ 2 + b ^ 2 = 1 - 2 * (a * b) := by
    linear_combination (a + b + 1) * h
  have hsq2 : u ^ 2 + v ^ 2 = (u * v) ^ 2 - 2 * (u * v) := by
    linear_combination (u + v + u * v) * hs
  nlinarith [hX', hsq, hsq2, hu, hv]

theorem s_recip_sq_sum (a b : ℝ) (ha : 0 < a) (hb : 0 < b) (h : a + b = 1) : 25 / 2 ≤ (a + 1 / a) ^ 2 + (b + 1 / b) ^ 2 := by
  have hu0 : 0 < 1 / a := by positivity
  have hv0 : 0 < 1 / b := by positivity
  have hu : a * (1 / a) = 1 := by
    rw [mul_one_div, div_self ha.ne']
  have hv : b * (1 / b) = 1 := by
    rw [mul_one_div, div_self hb.ne']
  exact s_recip_sq_sum_aux a b (1 / a) (1 / b) ha hb h hu hv hu0 hv0

#print axioms s_recip_sq_sum
