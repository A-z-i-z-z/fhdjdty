import Mathlib

open Finset

open Function Set
abbrev putnam_2013_b1_solution : ℤ := -1

theorem putnam_2013_b1_key (c : ℕ → ℤ) (hc1 : c 1 = 1)
    (hceven : ∀ n : ℕ, n > 0 → c (2 * n) = c n)
    (hcodd : ∀ n : ℕ, n > 0 → c (2 * n + 1) = (-1) ^ n * c n) :
    ∀ N : ℕ, ∑ n ∈ Finset.Icc 1 (2 * N + 1), c n * c (n + 2) = -1 := by
  intro N
  induction N with
  | zero =>
    have h3 : c 3 = -1 := by
      have h := hcodd 1 (by norm_num)
      norm_num [hc1] at h
      linarith
    norm_num [hc1, h3]
  | succ k ih =>
    rw [show 2 * (k + 1) + 1 = (2 * k + 1) + 1 + 1 by ring]
    rw [Finset.sum_Icc_succ_top (show 1 ≤ 2 * k + 1 + 1 + 1 by omega),
      Finset.sum_Icc_succ_top (show 1 ≤ 2 * k + 1 + 1 by omega), ih]
    have A : c (2 * k + 1 + 1) = c (k + 1) := by
      rw [show 2 * k + 1 + 1 = 2 * (k + 1) by ring]
      exact hceven (k + 1) (by omega)
    have B : c (2 * k + 1 + 1 + 2) = c (k + 2) := by
      rw [show 2 * k + 1 + 1 + 2 = 2 * (k + 2) by ring]
      exact hceven (k + 2) (by omega)
    have C : c (2 * k + 1 + 1 + 1) = (-1) ^ (k + 1) * c (k + 1) := by
      rw [show 2 * k + 1 + 1 + 1 = 2 * (k + 1) + 1 by ring]
      exact hcodd (k + 1) (by omega)
    have D : c (2 * k + 1 + 1 + 1 + 2) = (-1) ^ (k + 2) * c (k + 2) := by
      rw [show 2 * k + 1 + 1 + 1 + 2 = 2 * (k + 2) + 1 by ring]
      exact hcodd (k + 2) (by omega)
    have hs : (-1 : ℤ) ^ (k + 1) * (-1) ^ (k + 2) = -1 := by
      rw [← pow_add, show k + 1 + (k + 2) = 2 * k + 3 by ring, pow_add, pow_mul]
      norm_num
    simp only [A, B, C, D]
    linear_combination (c (k + 1) * c (k + 2)) * hs

theorem putnam_2013_b1 (c : ℕ → ℤ)
(hc1 : c 1 = 1)
(hceven : ∀ n : ℕ, n > 0 → c (2 * n) = c n)
(hcodd : ∀ n : ℕ, n > 0 → c (2 * n + 1) = (-1) ^ n * c n)
: (∑ n : Set.Icc 1 2013, c n * c (n.1 + 2)) = putnam_2013_b1_solution := by
  have h2 : (∑ n : Set.Icc 1 2013, c n * c (n.1 + 2))
      = ∑ n ∈ Finset.Icc 1 2013, c n * c (n + 2) :=
    (Finset.sum_subtype (p := fun x => x ∈ Set.Icc 1 2013) (Finset.Icc 1 2013)
      (by intro x; simp) (fun n => c n * c (n + 2))).symm
  rw [h2]
  have key := putnam_2013_b1_key c hc1 hceven hcodd 1006
  rw [show 2 * 1006 + 1 = 2013 by norm_num] at key
  exact key

#print axioms putnam_2013_b1
