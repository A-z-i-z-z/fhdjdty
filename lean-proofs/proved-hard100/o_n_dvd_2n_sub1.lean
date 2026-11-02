import Mathlib

open Finset

theorem zmod_nat_eq_zero_iff (p a : ℕ) [NeZero p] : ((a : ℕ) : ZMod p) = 0 ↔ p ∣ a := by
  first
    | exact ZMod.natCast_eq_zero_iff_dvd a p
    | exact ZMod.natCast_zmod_eq_zero_iff_dvd a p
    | exact ZMod.natCast_eq_zero_iff a p

theorem o_n_dvd_2n_sub1 (n : ℕ) (h : 0 < n) (hd : n ∣ 2 ^ n - 1) : n = 1 := by
  by_contra hne
  have hpow_even : 2 ∣ 2 ^ n := dvd_pow_self 2 (by omega)
  have h0 : 0 < 2 ^ n := by positivity
  have hpos1 : 1 ≤ 2 ^ n := by omega
  have hodd : ¬ 2 ∣ n := by
    intro h2n
    have h2d : 2 ∣ 2 ^ n - 1 := dvd_trans h2n hd
    omega
  obtain ⟨p, hpdef⟩ : ∃ p, p = Nat.minFac n := ⟨_, rfl⟩
  have hp : p.Prime := by
    rw [hpdef]
    exact Nat.minFac_prime hne
  have hpn : p ∣ n := by
    rw [hpdef]
    exact Nat.minFac_dvd n
  have hminle : ∀ q, q.Prime → q ∣ n → p ≤ q := by
    intro q hq hqn
    rw [hpdef]
    exact Nat.minFac_le_of_dvd hq.two_le hqn
  have hp2 : p ≠ 2 := by
    intro h2
    apply hodd
    rw [h2] at hpn
    exact hpn
  have hp3 : 3 ≤ p := by
    have := hp.two_le
    omega
  have hgcd : Nat.gcd n (p - 1) = 1 := by
    by_contra hg
    have hq : (Nat.gcd n (p - 1)).minFac.Prime := Nat.minFac_prime hg
    have hqd : (Nat.gcd n (p - 1)).minFac ∣ Nat.gcd n (p - 1) := Nat.minFac_dvd _
    have hqn : (Nat.gcd n (p - 1)).minFac ∣ n := dvd_trans hqd (Nat.gcd_dvd_left _ _)
    have hqp : (Nat.gcd n (p - 1)).minFac ∣ p - 1 := dvd_trans hqd (Nat.gcd_dvd_right _ _)
    have h1 : p ≤ (Nat.gcd n (p - 1)).minFac := hminle _ hq hqn
    have h2 : (Nat.gcd n (p - 1)).minFac ≤ p - 1 := Nat.le_of_dvd (by omega) hqp
    omega
  haveI : Fact p.Prime := Fact.mk hp
  haveI : NeZero p := ⟨hp.ne_zero⟩
  have h20 : (2 : ZMod p) ≠ 0 := by
    intro h
    have h' : ((2 : ℕ) : ZMod p) = 0 := by exact_mod_cast h
    have hdvd : p ∣ 2 := (zmod_nat_eq_zero_iff p 2).mp h'
    have := Nat.le_of_dvd (by norm_num) hdvd
    omega
  have hz : ((2 ^ n - 1 : ℕ) : ZMod p) = 0 :=
    (zmod_nat_eq_zero_iff p _).mpr (dvd_trans hpn hd)
  have hn2 : (2 : ZMod p) ^ n = 1 := by
    have hz' := hz
    push_cast [Nat.cast_sub hpos1] at hz'
    linear_combination hz'
  have hf : (2 : ZMod p) ^ (p - 1) = 1 := ZMod.pow_card_sub_one_eq_one h20
  have hg2 : (2 : ZMod p) ^ (Nat.gcd n (p - 1)) = 1 := by
    first
      | exact pow_gcd_eq_one.mpr ⟨hn2, hf⟩
      | exact pow_gcd_eq_one (2 : ZMod p) hn2 hf
  rw [hgcd, pow_one] at hg2
  have h10 : (1 : ZMod p) = 0 := by linear_combination hg2
  have hdvd1 : p ∣ 1 := (zmod_nat_eq_zero_iff p 1).mp (by exact_mod_cast h10)
  have := Nat.le_of_dvd (by norm_num) hdvd1
  omega

#print axioms o_n_dvd_2n_sub1
