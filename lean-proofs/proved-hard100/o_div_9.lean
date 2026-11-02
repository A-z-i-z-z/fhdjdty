import Mathlib

open Finset

theorem o_div_9_aux (n : ℕ) : (4 ^ n + 15 * n) % 9 = 1 := by
  induction n with
  | zero => norm_num
  | succ k ih =>
    rw [pow_succ]
    omega

theorem o_div_9 (n : ℕ) : 9 ∣ 4 ^ n + 15 * n - 1 := by
  have h := o_div_9_aux n
  have hpos : 0 < 4 ^ n := by positivity
  omega

#print axioms o_div_9
