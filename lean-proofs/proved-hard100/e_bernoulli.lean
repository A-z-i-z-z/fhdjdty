import Mathlib

open Finset

theorem e_bernoulli (x : ℝ) (hx : -1 ≤ x) (n : ℕ) : 1 + n * x ≤ (1 + x) ^ n := by
  have h1 : (0:ℝ) ≤ 1 + x := by linarith
  induction n with
  | zero => simp
  | succ k ih =>
    rw [pow_succ]
    push_cast
    have h2 : (0:ℝ) ≤ (k:ℝ) := Nat.cast_nonneg k
    nlinarith [mul_nonneg (sub_nonneg.mpr ih) h1, mul_nonneg h2 (sq_nonneg x)]

#print axioms e_bernoulli
