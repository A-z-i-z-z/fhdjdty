import Mathlib

open Finset

open Topology Filter

theorem putnam_2000_a2_pell_aux (k : ℕ) :
    ∃ x a : ℕ, x ^ 2 = 2 * a ^ 2 + 1 ∧ k ≤ a ∧ 1 ≤ x := by
  induction k with
  | zero => exact ⟨3, 2, by norm_num, by norm_num, by norm_num⟩
  | succ k ih =>
    obtain ⟨x, a, h, hka, hx⟩ := ih
    refine ⟨3 * x + 4 * a, 2 * x + 3 * a, ?_, ?_, ?_⟩
    · nlinarith [h]
    · omega
    · omega

theorem putnam_2000_a2 :
  ∀ n : ℕ,
    ∃ N : ℤ,
      ∃ i : Fin 6 → ℕ,
        N > n ∧
        N = (i 0)^2 + (i 1)^2 ∧
        N + 1 = (i 2)^2 + (i 3)^2 ∧
        N + 2 = (i 4)^2 + (i 5)^2 := by
  intro n
  obtain ⟨x, a, h, hna, hx⟩ := putnam_2000_a2_pell_aux (n + 1)
  obtain ⟨m, rfl⟩ : ∃ m, a = m + 1 := ⟨a - 1, by omega⟩
  have hz : (x : ℤ) ^ 2 = 2 * ((m : ℤ) + 1) ^ 2 + 1 := by
    exact_mod_cast h
  have ht : (n : ℤ) + 1 ≤ (m : ℤ) + 1 := by
    exact_mod_cast hna
  have hm : (0 : ℤ) ≤ (m : ℤ) := Nat.cast_nonneg m
  refine ⟨2 * ((m : ℤ) + 1) ^ 2, ![m + 1, m + 1, x, 0, m + 2, m], ?_, ?_, ?_, ?_⟩
  · nlinarith [sq_nonneg (m : ℤ), hm, ht]
  · norm_num <;> ring
  · norm_num <;> linarith [hz]
  · norm_num <;> ring

#print axioms putnam_2000_a2
