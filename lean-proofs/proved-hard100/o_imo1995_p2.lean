import Mathlib

open Finset

theorem o_imo1995_p2 (a b c : ℝ) (ha : 0 < a) (hb : 0 < b) (hc : 0 < c) (h : a * b * c = 1) : 3 / 2 ≤ 1 / (a ^ 3 * (b + c)) + 1 / (b ^ 3 * (c + a)) + 1 / (c ^ 3 * (a + b)) := by
  have key : ∀ x y z : ℝ, 0 < x → 0 < y → 0 < z → x * y * z = 1 →
      3 / 2 ≤ x ^ 2 / (y + z) + y ^ 2 / (z + x) + z ^ 2 / (x + y) := by
    intro x y z hx hy hz hxyz
    have ht1 : 0 < y + z := by linarith
    have ht2 : 0 < z + x := by linarith
    have ht3 : 0 < x + y := by linarith
    have h1 : x - (y + z) / 4 ≤ x ^ 2 / (y + z) := by
      rw [le_div_iff₀ ht1]
      nlinarith [sq_nonneg (x - (y + z) / 2)]
    have h2 : y - (z + x) / 4 ≤ y ^ 2 / (z + x) := by
      rw [le_div_iff₀ ht2]
      nlinarith [sq_nonneg (y - (z + x) / 2)]
    have h3 : z - (x + y) / 4 ≤ z ^ 2 / (x + y) := by
      rw [le_div_iff₀ ht3]
      nlinarith [sq_nonneg (z - (x + y) / 2)]
    have hs0 : 0 < x + y + z := by linarith
    have hcube : (x + y + z) ^ 3 ≥ 27 := by
      nlinarith [mul_nonneg hs0.le (sq_nonneg (x - y)), mul_nonneg hs0.le (sq_nonneg (y - z)),
        mul_nonneg hs0.le (sq_nonneg (z - x)), mul_nonneg hx.le (sq_nonneg (y - z)),
        mul_nonneg hy.le (sq_nonneg (z - x)), mul_nonneg hz.le (sq_nonneg (x - y))]
    have hs : x + y + z ≥ 3 := by
      nlinarith [sq_nonneg (x + y + z), hs0]
    linarith [h1, h2, h3, hs]
  have hab := mul_pos ha hb
  have hbc := mul_pos hb hc
  have hca := mul_pos hc ha
  have hsq : (a * b * c) ^ 2 = 1 := by
    rw [h]
    norm_num
  have e1 : 1 / (a ^ 3 * (b + c)) = (b * c) ^ 2 / (c * a + a * b) := by
    rw [div_eq_div_iff (by positivity) (by positivity)]
    linear_combination (-(a * (b + c))) * hsq
  have e2 : 1 / (b ^ 3 * (c + a)) = (c * a) ^ 2 / (a * b + b * c) := by
    rw [div_eq_div_iff (by positivity) (by positivity)]
    linear_combination (-(b * (c + a))) * hsq
  have e3 : 1 / (c ^ 3 * (a + b)) = (a * b) ^ 2 / (b * c + c * a) := by
    rw [div_eq_div_iff (by positivity) (by positivity)]
    linear_combination (-(c * (a + b))) * hsq
  rw [e1, e2, e3]
  exact key (b * c) (c * a) (a * b) hbc hca hab (by linear_combination hsq)

#print axioms o_imo1995_p2
