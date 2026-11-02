import Mathlib

open Finset

theorem o_rec_second (a : ℕ → ℤ) (h0 : a 0 = 0) (h1 : a 1 = 1) (h : ∀ n, a (n + 2) = 3 * a (n + 1) - 2 * a n) : ∀ n, a n = 2 ^ n - 1 := by
  have key : ∀ n, a n = 2 ^ n - 1 ∧ a (n + 1) = 2 ^ (n + 1) - 1 := by
    intro n
    induction n with
    | zero =>
      constructor <;> norm_num [h0, h1]
    | succ k ih =>
      refine ⟨ih.2, ?_⟩
      have e : a (k + 1 + 1) = 3 * a (k + 1) - 2 * a k := h k
      rw [e, ih.1, ih.2]
      ring
  intro n
  exact (key n).1

#print axioms o_rec_second
