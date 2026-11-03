import Mathlib

open Finset

open Topology Filter
abbrev putnam_2017_b2_solution : ℕ := 16

theorem putnam_sum_aux (x : ℤ) (k : ℕ) :
    2 * (∑ i ∈ Finset.range k, (x + (i:ℤ))) = 2 * (k:ℤ) * x + (k:ℤ) * ((k:ℤ) - 1) := by
  induction k with
  | zero => simp
  | succ n ih =>
    rw [Finset.sum_range_succ, mul_add, ih]
    push_cast
    ring

theorem putnam_2017_b2 (S : ℤ → ℕ → ℤ)
  (p : ℤ → ℕ → Prop)
  (q : ℤ → Prop)
  (hS : S = fun (a : ℤ) k ↦ ∑ i ∈ Finset.range k, (a + i))
  (hp : ∀ N k, p N k ↔ ∃ a > 0, S a k = N)
  (hq : ∀ N, q N ↔ p N 2017 ∧ ∀ k : ℕ, k > 1 → k ≠ 2017 → ¬p N k) :
  IsLeast {a : ℤ | q (S a 2017)} putnam_2017_b2_solution := by
  have hc : ((putnam_2017_b2_solution : ℕ) : ℤ) = 16 := by
    simp [putnam_2017_b2_solution]
  have key : ∀ (a : ℤ) (k : ℕ), 2 * S a k = 2 * (k:ℤ) * a + (k:ℤ) * ((k:ℤ) - 1) := by
    intro a k
    rw [hS]
    exact putnam_sum_aux a k
  have rep : ∀ (N : ℤ) (k : ℕ) (b : ℤ), 0 < b →
      2 * (k:ℤ) * b + (k:ℤ) * ((k:ℤ) - 1) = 2 * N → p N k := by
    intro N k b hb h
    refine (hp N k).mpr ⟨b, hb, ?_⟩
    have := key b k
    linarith
  constructor
  · show q (S ((putnam_2017_b2_solution : ℕ) : ℤ) 2017)
    rw [hc]
    have hN : 2 * S 16 2017 = 4130816 := by
      rw [key]
      norm_num
    refine (hq _).mpr ⟨(hp _ _).mpr ⟨16, by norm_num, rfl⟩, ?_⟩
    intro k hk1 hk2 hpk
    obtain ⟨b, hb, hbk⟩ := (hp _ _).mp hpk
    have hfin : 2 * (k:ℤ) * b + (k:ℤ) * ((k:ℤ) - 1) = 4130816 := by
      rw [← key b k, hbk]
      exact hN
    have hk0 : (0:ℤ) ≤ (k:ℤ) := by positivity
    have hb1 : (1:ℤ) ≤ b := by omega
    have hk3 : (k:ℤ) ≤ 2032 := by
      nlinarith [mul_nonneg hk0 (sub_nonneg.mpr hb1)]
    have hk4 : k ≤ 2032 := by omega
    set_option maxHeartbeats 4000000 in
    interval_cases k <;> first | omega | (norm_num at hfin <;> omega)
  · intro a ha
    have ha' : q (S a 2017) := ha
    obtain ⟨hp1, hother⟩ := (hq _).mp ha'
    rw [hc]
    obtain ⟨b, hb, hbk⟩ := (hp _ _).mp hp1
    have hab : b = a := by
      have h1 := key b 2017
      have h2 := key a 2017
      rw [hbk] at h1
      push_cast at h1 h2
      linarith
    by_contra hlt
    push_neg at hlt
    have ha1 : 1 ≤ a := by omega
    have ha2 : a ≤ 15 := by omega
    have hcontra : ∀ (k : ℕ) (b' : ℤ), 1 < k → k ≠ 2017 → 0 < b' →
        2 * (k:ℤ) * b' + (k:ℤ) * ((k:ℤ) - 1) = 2 * S a 2017 → False :=
      fun k b' hk1 hk2 hb' h => hother k hk1 hk2 (rep _ k b' hb' h)
    interval_cases a
    · exact hcontra 1009 1513 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 505 3782 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 1011 1512 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 253 7942 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 1013 1511 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 507 3781 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 1015 1510 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 127 16073 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 1017 1509 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 509 3780 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 1019 1508 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 255 7941 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 1021 1507 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 511 3779 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)
    · exact hcontra 1023 1506 (by norm_num) (by norm_num) (by norm_num) (by rw [key]; norm_num)

#print axioms putnam_2017_b2
