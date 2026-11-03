import Mathlib

open Finset

open Topology Filter
abbrev putnam_2019_a1_solution : Set ℤ := {n : ℤ | n ≥ 0 ∧ ¬Int.ModEq 9 n 3 ∧ ¬Int.ModEq 9 n 6}

lemma putnam_2019_a1_zmod : ∀ a b c : ZMod 9, a^3+b^3+c^3-3*a*b*c ≠ 3 ∧ a^3+b^3+c^3-3*a*b*c ≠ 6 := by
  decide

lemma putnam_2019_a1_mod (A B C : ℤ) : (A^3+B^3+C^3-3*A*B*C) % 9 ≠ 3 ∧ (A^3+B^3+C^3-3*A*B*C) % 9 ≠ 6 := by
  obtain ⟨h1, h2⟩ := putnam_2019_a1_zmod (A : ZMod 9) (B : ZMod 9) (C : ZMod 9)
  constructor
  · intro h
    have hc : ((A^3+B^3+C^3-3*A*B*C : ℤ) : ZMod 9) = ((3:ℤ) : ZMod 9) := by
      rw [ZMod.intCast_eq_intCast_iff']
      simpa using h
    push_cast at hc
    exact h1 hc
  · intro h
    have hc : ((A^3+B^3+C^3-3*A*B*C : ℤ) : ZMod 9) = ((6:ℤ) : ZMod 9) := by
      rw [ZMod.intCast_eq_intCast_iff']
      simpa using h
    push_cast at hc
    exact h2 hc

theorem putnam_2019_a1 : {n : ℤ | ∃ A B C : ℤ, A ≥ 0 ∧ B ≥ 0 ∧ C ≥ 0 ∧ A^3 + B^3 + C^3 - 3*A*B*C = n} = putnam_2019_a1_solution := by
  ext n
  simp only [putnam_2019_a1_solution, Set.mem_setOf_eq]
  constructor
  · rintro ⟨A, B, C, hA, hB, hC, rfl⟩
    obtain ⟨h1, h2⟩ := putnam_2019_a1_mod A B C
    refine ⟨?_, ?_, ?_⟩
    · have hQ : 0 ≤ (A+B+C)*((A-B)^2+(B-C)^2+(C-A)^2) :=
        mul_nonneg (by linarith) (by positivity)
      nlinarith [hQ]
    · intro h
      apply h1
      unfold Int.ModEq at h
      simpa using h
    · intro h
      apply h2
      unfold Int.ModEq at h
      simpa using h
  · rintro ⟨hn, h3, h6⟩
    have hm : n % 9 ≠ 3 ∧ n % 9 ≠ 6 := by
      unfold Int.ModEq at h3 h6
      omega
    have h3cases : n % 3 = 0 ∨ n % 3 = 1 ∨ n % 3 = 2 := by omega
    rcases h3cases with h | h | h
    · have h9 : n % 9 = 0 := by omega
      obtain ⟨a, rfl⟩ : ∃ a, n = 9 * a := ⟨n / 9, by omega⟩
      by_cases ha : a = 0
      · subst ha
        exact ⟨0, 0, 0, by norm_num⟩
      · exact ⟨a+1, a, a-1, by omega, by omega, by omega, by ring⟩
    · obtain ⟨k, rfl⟩ : ∃ k, n = 3 * k + 1 := ⟨n / 3, by omega⟩
      exact ⟨k+1, k, k, by omega, by omega, by omega, by ring⟩
    · obtain ⟨k, rfl⟩ : ∃ k, n = 3 * k + 2 := ⟨n / 3, by omega⟩
      exact ⟨k+1, k+1, k, by omega, by omega, by omega, by ring⟩

#print axioms putnam_2019_a1
