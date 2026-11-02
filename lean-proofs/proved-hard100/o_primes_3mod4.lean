import Mathlib

open Finset

theorem o_aux_three_mod_four (m : ℕ) : m % 4 = 3 → ∃ q, q.Prime ∧ q ∣ m ∧ q % 4 = 3 := by
  induction m using Nat.strong_induction_on with
  | _ m ih =>
    intro hm
    have hm1 : m ≠ 1 := by omega
    have hpr : (Nat.minFac m).Prime := Nat.minFac_prime hm1
    obtain ⟨k, hk⟩ := Nat.minFac_dvd m
    by_cases h3 : Nat.minFac m % 4 = 3
    · exact ⟨Nat.minFac m, hpr, Nat.minFac_dvd m, h3⟩
    · have h2 : Nat.minFac m ≠ 2 := by
        intro h
        rw [h] at hk
        omega
      have hodd : Nat.minFac m % 2 = 1 := Nat.odd_iff.mp (hpr.odd_of_ne_two h2)
      have hp1 : Nat.minFac m % 4 = 1 := by omega
      have hmod : (Nat.minFac m * k) % 4 = 3 := by
        rw [← hk]
        exact hm
      have hk3' : (Nat.minFac m % 4) * (k % 4) % 4 = 3 := by
        rw [← Nat.mul_mod]
        exact hmod
      rw [hp1] at hk3'
      have hk3 : k % 4 = 3 := by omega
      have hkpos : 0 < k := by
        rcases Nat.eq_zero_or_pos k with h0 | h0
        · rw [h0, mul_zero] at hk
          omega
        · exact h0
      have hklt : k < m := by nlinarith [hpr.two_le, hkpos]
      obtain ⟨q, hq, hqk, hq3⟩ := ih k hklt hk3
      have hkm : k ∣ m := ⟨Nat.minFac m, by rw [mul_comm]; exact hk⟩
      exact ⟨q, hq, hqk.trans hkm, hq3⟩

theorem o_primes_3mod4 : ∀ N : ℕ, ∃ p, N < p ∧ p.Prime ∧ p % 4 = 3 := by
  intro N
  have hfpos : 0 < Nat.factorial N := Nat.factorial_pos N
  have hM3 : (4 * Nat.factorial N - 1) % 4 = 3 := by omega
  obtain ⟨p, hp, hpM, hp3⟩ := o_aux_three_mod_four (4 * Nat.factorial N - 1) hM3
  refine ⟨p, ?_, hp, hp3⟩
  by_contra hle
  push_neg at hle
  have hdf : p ∣ Nat.factorial N := Nat.dvd_factorial hp.pos hle
  have hpF : p ∣ 4 * Nat.factorial N := Dvd.dvd.mul_left hdf 4
  have hM1eq : 4 * Nat.factorial N - 1 + 1 = 4 * Nat.factorial N := by omega
  have hpM1 : p ∣ 4 * Nat.factorial N - 1 + 1 := by
    rw [hM1eq]
    exact hpF
  have h1 : p ∣ 1 := by
    first
    | exact (Nat.dvd_add_right hpM).mp hpM1
    | exact (Nat.dvd_add_iff_right hpM).mpr hpM1
    | simpa using Nat.dvd_sub hpM1 hpM
    | simpa using Nat.dvd_sub' hpM1 hpM
  have h2 := Nat.dvd_one.mp h1
  have h3 := hp.one_lt
  omega

#print axioms o_primes_3mod4
