import Mathlib

open Finset

theorem o_rec_linear (a : ℕ → ℝ) (h0 : a 0 = 1) (h : ∀ n, a (n + 1) = 2 * a n + 1) : ∀ n, a n = 2 ^ (n + 1) - 1 := by
  intro n
  induction n with
  | zero => norm_num [h0]
  | succ k ih =>
    rw [h k, ih]
    ring

#print axioms o_rec_linear
