import Mathlib

open Finset

open Filter Topology
abbrev putnam_2021_a5_solution : Set ℕ := {j : ℕ | ¬(42 ∣ j) ∧ ¬(46 ∣ j)}

theorem pa5_fermat (p n j : ℕ) [Fact p.Prime] (hn : ¬ p ∣ n) (hj : (p - 1) ∣ j) :
    ((n : ℕ) : ZMod p) ^ j = 1 := by
  have hne : ((n : ℕ) : ZMod p) ≠ 0 := fun h0 => hn ((CharP.cast_eq_zero_iff (ZMod p) p n).mp h0)
  obtain ⟨k, hk⟩ := hj
  rw [hk, pow_mul, ZMod.pow_card_sub_one_eq_one hne, one_pow]

theorem pa5_cancel (p : ℕ) [Fact p.Prime] (c T : ZMod p) (h1 : c ≠ 1) (h : c * T = T) :
    T = 0 := by
  have h2 : (c - 1) * T = 0 := by
    rw [sub_mul, h, one_mul, sub_self]
  rcases mul_eq_zero.mp h2 with h3 | h3
  · exact absurd (sub_eq_zero.mp h3) h1
  · exact h3

theorem pa5_pow_ne_one (m p : ℕ) (a : ZMod p) (hm : a ^ m = 1)
    (hr : ∀ r, r < m → r ≠ 0 → a ^ r ≠ 1) (j : ℕ) (hpos : 0 < m) (hj : j % m ≠ 0) :
    a ^ j ≠ 1 := by
  have e : a ^ j = (a ^ m) ^ (j / m) * a ^ (j % m) := by
    rw [← pow_mul, ← pow_add, Nat.div_add_mod]
  have hjr : a ^ j = a ^ (j % m) := by
    rw [e, hm, one_pow, one_mul]
  rw [hjr]
  exact hr (j % m) (Nat.mod_lt _ hpos) hj

theorem pa5_three_ne (j : ℕ) (hj : ¬ 42 ∣ j) : (3 : ZMod 43) ^ j ≠ 1 := by
  have h42 : (3 : ZMod 43) ^ 42 = 1 := by decide +kernel
  have hr : ∀ r, r < 42 → r ≠ 0 → (3 : ZMod 43) ^ r ≠ 1 := by decide +kernel
  exact pa5_pow_ne_one 42 43 3 h42 hr j (by norm_num) (by omega)

theorem pa5_five_ne (j : ℕ) (hj : ¬ 46 ∣ j) : (5 : ZMod 47) ^ j ≠ 1 := by
  have h46 : (5 : ZMod 47) ^ 46 = 1 := by decide +kernel
  have hr : ∀ r, r < 46 → r ≠ 0 → (5 : ZMod 47) ^ r ≠ 1 := by decide +kernel
  exact pa5_pow_ne_one 46 47 5 h46 hr j (by norm_num) (by omega)

theorem pa5_sum_cast (p j : ℕ) [Fact p.Prime] (A : Finset ℕ) (hn : ∀ n ∈ A, ¬ p ∣ n)
    (hj : (p - 1) ∣ j) :
    ((∑ n ∈ A, n ^ j : ℕ) : ZMod p) = (A.card : ZMod p) := by
  rw [Nat.cast_sum]
  have h1 : ∀ n ∈ A, ((n ^ j : ℕ) : ZMod p) = 1 := by
    intro n hn'
    rw [Nat.cast_pow]
    exact pa5_fermat p n j (hn n hn') hj
  rw [Finset.sum_congr rfl h1, Finset.sum_const, nsmul_eq_mul, mul_one]

theorem pa5_cast43 (A : Finset ℕ) (j : ℕ) (hA : ∀ n ∈ A, n < 2022) :
    ((∑ n ∈ A, (1035 * n % 2021) ^ j : ℕ) : ZMod 43)
      = (3 : ZMod 43) ^ j * ((∑ n ∈ A, n ^ j : ℕ) : ZMod 43) := by
  have hdec : ∀ n, n < 2022 → (1035 * n % 2021) % 43 = (3 * n) % 43 := by
    decide +kernel
  rw [Nat.cast_sum, Nat.cast_sum, Finset.mul_sum]
  apply Finset.sum_congr rfl
  intro n hn
  have hc : ((1035 * n % 2021 : ℕ) : ZMod 43) = 3 * (n : ZMod 43) := by
    calc ((1035 * n % 2021 : ℕ) : ZMod 43)
        = (((1035 * n % 2021) % 43 : ℕ) : ZMod 43) := by rw [ZMod.natCast_mod]
      _ = (((3 * n) % 43 : ℕ) : ZMod 43) := by rw [hdec n (hA n hn)]
      _ = ((3 * n : ℕ) : ZMod 43) := by rw [ZMod.natCast_mod]
      _ = 3 * (n : ZMod 43) := by simp [Nat.cast_mul]
  rw [Nat.cast_pow, Nat.cast_pow, hc, mul_pow]

theorem pa5_cast47 (A : Finset ℕ) (j : ℕ) (hA : ∀ n ∈ A, n < 2022) :
    ((∑ n ∈ A, (1979 * n % 2021) ^ j : ℕ) : ZMod 47)
      = (5 : ZMod 47) ^ j * ((∑ n ∈ A, n ^ j : ℕ) : ZMod 47) := by
  have hdec : ∀ n, n < 2022 → (1979 * n % 2021) % 47 = (5 * n) % 47 := by
    decide +kernel
  rw [Nat.cast_sum, Nat.cast_sum, Finset.mul_sum]
  apply Finset.sum_congr rfl
  intro n hn
  have hc : ((1979 * n % 2021 : ℕ) : ZMod 47) = 5 * (n : ZMod 47) := by
    calc ((1979 * n % 2021 : ℕ) : ZMod 47)
        = (((1979 * n % 2021) % 47 : ℕ) : ZMod 47) := by rw [ZMod.natCast_mod]
      _ = (((5 * n) % 47 : ℕ) : ZMod 47) := by rw [hdec n (hA n hn)]
      _ = ((5 * n : ℕ) : ZMod 47) := by rw [ZMod.natCast_mod]
      _ = 5 * (n : ZMod 47) := by simp [Nat.cast_mul]
  rw [Nat.cast_pow, Nat.cast_pow, hc, mul_pow]

theorem pa5_perm1 (A : Finset ℕ)
    (hmem : ∀ n, n ∈ A ↔ (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1)) (j : ℕ) :
    ∑ n ∈ A, (1035 * n % 2021) ^ j = ∑ n ∈ A, n ^ j := by
  have f1 : ∀ n, n < 2022 → (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) →
      (1 ≤ 1035 * n % 2021 ∧ 1035 * n % 2021 ≤ 2021 ∧ Nat.gcd (1035 * n % 2021) 2021 = 1) := by
    decide +kernel
  have f2 : ∀ n, n < 2022 → (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) →
      (1 ≤ 330 * n % 2021 ∧ 330 * n % 2021 ≤ 2021 ∧ Nat.gcd (330 * n % 2021) 2021 = 1) := by
    decide +kernel
  have f3 : ∀ n, n < 2022 → (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) →
      330 * (1035 * n % 2021) % 2021 = n := by
    decide +kernel
  have f4 : ∀ n, n < 2022 → (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) →
      1035 * (330 * n % 2021) % 2021 = n := by
    decide +kernel
  refine Finset.sum_nbij' (fun n => 1035 * n % 2021) (fun n => 330 * n % 2021)
    ?_ ?_ ?_ ?_ ?_
  · intro a ha
    rw [hmem] at ha ⊢
    exact f1 a (by have := ha.2.1; omega) ha
  · intro a ha
    rw [hmem] at ha ⊢
    exact f2 a (by have := ha.2.1; omega) ha
  · intro a ha
    rw [hmem] at ha
    exact f3 a (by have := ha.2.1; omega) ha
  · intro a ha
    rw [hmem] at ha
    exact f4 a (by have := ha.2.1; omega) ha
  · intro a _
    rfl

theorem pa5_perm2 (A : Finset ℕ)
    (hmem : ∀ n, n ∈ A ↔ (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1)) (j : ℕ) :
    ∑ n ∈ A, (1979 * n % 2021) ^ j = ∑ n ∈ A, n ^ j := by
  have g1 : ∀ n, n < 2022 → (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) →
      (1 ≤ 1979 * n % 2021 ∧ 1979 * n % 2021 ≤ 2021 ∧ Nat.gcd (1979 * n % 2021) 2021 = 1) := by
    decide +kernel
  have g2 : ∀ n, n < 2022 → (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) →
      (1 ≤ 818 * n % 2021 ∧ 818 * n % 2021 ≤ 2021 ∧ Nat.gcd (818 * n % 2021) 2021 = 1) := by
    decide +kernel
  have g3 : ∀ n, n < 2022 → (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) →
      818 * (1979 * n % 2021) % 2021 = n := by
    decide +kernel
  have g4 : ∀ n, n < 2022 → (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) →
      1979 * (818 * n % 2021) % 2021 = n := by
    decide +kernel
  refine Finset.sum_nbij' (fun n => 1979 * n % 2021) (fun n => 818 * n % 2021)
    ?_ ?_ ?_ ?_ ?_
  · intro a ha
    rw [hmem] at ha ⊢
    exact g1 a (by have := ha.2.1; omega) ha
  · intro a ha
    rw [hmem] at ha ⊢
    exact g2 a (by have := ha.2.1; omega) ha
  · intro a ha
    rw [hmem] at ha
    exact g3 a (by have := ha.2.1; omega) ha
  · intro a ha
    rw [hmem] at ha
    exact g4 a (by have := ha.2.1; omega) ha
  · intro a _
    rfl

theorem putnam_2021_a5 (j : ℕ)
  (A : Finset ℕ)
  (S : ℕ → ℕ)
  (hA : A = {n | 1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1})
  (hS : ∀ j' : ℕ, S j' = ∑ n ∈ A, n ^ j') :
  (2021 ∣ S j) ↔ j ∈ putnam_2021_a5_solution := by
  haveI fact43 : Fact (Nat.Prime 43) := ⟨by norm_num⟩
  haveI fact47 : Fact (Nat.Prime 47) := ⟨by norm_num⟩
  have hmem : ∀ n, n ∈ A ↔ (1 ≤ n ∧ n ≤ 2021 ∧ Nat.gcd n 2021 = 1) := by
    intro n
    have h := Set.ext_iff.mp hA n
    simpa using h
  have hAlt : ∀ n ∈ A, n < 2022 := fun n hn => by
    have := ((hmem n).mp hn).2.1
    omega
  have hnot43 : ∀ n ∈ A, ¬ 43 ∣ n := by
    intro n hn hd
    have h1 := ((hmem n).mp hn).2.2
    have h2 : 43 ∣ Nat.gcd n 2021 := Nat.dvd_gcd hd (by norm_num)
    rw [h1] at h2
    norm_num at h2
  have hnot47 : ∀ n ∈ A, ¬ 47 ∣ n := by
    intro n hn hd
    have h1 := ((hmem n).mp hn).2.2
    have h2 : 47 ∣ Nat.gcd n 2021 := Nat.dvd_gcd hd (by norm_num)
    rw [h1] at h2
    norm_num at h2
  have hcard : A.card = 1932 := by
    have hAeq : A = (Finset.Icc 1 2021).filter (fun n => Nat.gcd n 2021 = 1) := by
      ext n
      rw [hmem n, Finset.mem_filter, Finset.mem_Icc]
      tauto
    rw [hAeq]
    decide +kernel
  have hcard43 : ¬ 43 ∣ A.card := by
    rw [hcard]
    norm_num
  have hcard47 : ¬ 47 ∣ A.card := by
    rw [hcard]
    norm_num
  have hbwd43 : 42 ∣ j → ¬ 43 ∣ S j := by
    intro hdvd hS43
    have hz := pa5_sum_cast 43 j A hnot43 (show (43 - 1) ∣ j from hdvd)
    have h0 : ((S j : ℕ) : ZMod 43) = 0 :=
      (CharP.cast_eq_zero_iff (ZMod 43) 43 (S j)).mpr hS43
    rw [hS j, hz] at h0
    exact hcard43 ((CharP.cast_eq_zero_iff (ZMod 43) 43 A.card).mp h0)
  have hbwd47 : 46 ∣ j → ¬ 47 ∣ S j := by
    intro hdvd hS47
    have hz := pa5_sum_cast 47 j A hnot47 (show (47 - 1) ∣ j from hdvd)
    have h0 : ((S j : ℕ) : ZMod 47) = 0 :=
      (CharP.cast_eq_zero_iff (ZMod 47) 47 (S j)).mpr hS47
    rw [hS j, hz] at h0
    exact hcard47 ((CharP.cast_eq_zero_iff (ZMod 47) 47 A.card).mp h0)
  have hfwd43 : ¬ 42 ∣ j → 43 ∣ S j := by
    intro hj
    have hc := pa5_cast43 A j hAlt
    rw [pa5_perm1 A hmem j] at hc
    have hc0 : ((∑ n ∈ A, n ^ j : ℕ) : ZMod 43) = 0 :=
      pa5_cancel 43 _ _ (pa5_three_ne j hj) hc.symm
    rw [hS j]
    exact (CharP.cast_eq_zero_iff (ZMod 43) 43 _).mp hc0
  have hfwd47 : ¬ 46 ∣ j → 47 ∣ S j := by
    intro hj
    have hc := pa5_cast47 A j hAlt
    rw [pa5_perm2 A hmem j] at hc
    have hc0 : ((∑ n ∈ A, n ^ j : ℕ) : ZMod 47) = 0 :=
      pa5_cancel 47 _ _ (pa5_five_ne j hj) hc.symm
    rw [hS j]
    exact (CharP.cast_eq_zero_iff (ZMod 47) 47 _).mp hc0
  constructor
  · intro h2021
    have h43' : 43 ∣ S j := Nat.dvd_trans (by norm_num) h2021
    have h47' : 47 ∣ S j := Nat.dvd_trans (by norm_num) h2021
    show ¬ 42 ∣ j ∧ ¬ 46 ∣ j
    exact ⟨fun h => hbwd43 h h43', fun h => hbwd47 h h47'⟩
  · intro hj
    have hj' : ¬ 42 ∣ j ∧ ¬ 46 ∣ j := hj
    have h43' : 43 ∣ S j := hfwd43 hj'.1
    have h47' : 47 ∣ S j := hfwd47 hj'.2
    rw [show (2021 : ℕ) = 43 * 47 by norm_num]
    exact Nat.Coprime.mul_dvd_of_dvd_of_dvd (by norm_num) h43' h47'

#print axioms putnam_2021_a5
