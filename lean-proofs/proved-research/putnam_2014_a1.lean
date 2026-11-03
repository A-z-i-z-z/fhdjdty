import Mathlib

open Finset

open Topology Filter

lemma p14_dvd_fact (a b : ℕ) (ha : 0 < a) (hab : a < b) :
    a * b ∣ Nat.factorial b := by
  have h1 : a ∣ Nat.factorial (b - 1) := Nat.dvd_factorial ha (by omega)
  have h2 : Nat.factorial b = b * Nat.factorial (b - 1) := by
    obtain ⟨c, rfl⟩ : ∃ c, b = c + 1 := ⟨b - 1, by omega⟩
    rw [Nat.factorial_succ]
    simp
  rw [h2, mul_comm a b]
  exact mul_dvd_mul_left b h1

lemma p14_comp (m : ℕ) (hm : 2 ≤ m) (hnp : ¬ Nat.Prime m) (h4 : m ≠ 4) :
    m ∣ Nat.factorial (m - 1) := by
  obtain ⟨p, hp, hpm⟩ : ∃ p, Nat.Prime p ∧ p ∣ m := Nat.exists_prime_and_dvd (by omega)
  obtain ⟨d, hd⟩ := hpm
  have hp2 : 2 ≤ p := hp.two_le
  have hd2 : 2 ≤ d := by
    by_contra hcon
    have hd1 : d = 0 ∨ d = 1 := by omega
    rcases hd1 with h0 | h1
    · rw [h0, mul_zero] at hd
      omega
    · rw [h1, mul_one] at hd
      exact hnp (by rw [hd]; exact hp)
  have hA : 2 * d ≤ m := by rw [hd]; nlinarith
  have hB : 2 * p ≤ m := by rw [hd]; nlinarith
  rcases lt_trichotomy p d with hlt | heq | hgt
  · have h1 : p * d ∣ Nat.factorial d := p14_dvd_fact p d (by omega) hlt
    have h2 : Nat.factorial d ∣ Nat.factorial (m - 1) :=
      Nat.factorial_dvd_factorial (by omega)
    have h3 : m ∣ p * d := ⟨1, by rw [mul_one]; exact hd.symm⟩
    exact dvd_trans h3 (dvd_trans h1 h2)
  · rw [← heq] at hd
    have hp3 : 3 ≤ p := by
      by_contra hcon
      have hp2' : p = 2 := by omega
      apply h4
      simp [hd, hp2']
    have hlt : 2 * p < m := by rw [hd]; nlinarith
    have h1 : p * (2 * p) ∣ Nat.factorial (2 * p) :=
      p14_dvd_fact p (2 * p) (by omega) (by omega)
    have h2 : Nat.factorial (2 * p) ∣ Nat.factorial (m - 1) :=
      Nat.factorial_dvd_factorial (by omega)
    have h3 : m ∣ p * (2 * p) := ⟨2, by rw [hd]; ring⟩
    exact dvd_trans h3 (dvd_trans h1 h2)
  · have h1 : d * p ∣ Nat.factorial p := p14_dvd_fact d p (by omega) hgt
    have h2 : Nat.factorial p ∣ Nat.factorial (m - 1) :=
      Nat.factorial_dvd_factorial (by omega)
    have h3 : m ∣ d * p := ⟨1, by rw [mul_one, mul_comm]; exact hd.symm⟩
    exact dvd_trans h3 (dvd_trans h1 h2)

lemma p14_trich (m : ℕ) (hm : 1 ≤ m) :
    Nat.Prime m ∨ m ∣ Nat.factorial (m - 1) ∨ m = 4 := by
  by_cases hp : Nat.Prime m
  · exact Or.inl hp
  by_cases h4 : m = 4
  · exact Or.inr (Or.inr h4)
  by_cases h1 : m = 1
  · subst h1
    exact Or.inr (Or.inl (by simp))
  · exact Or.inr (Or.inl (p14_comp m (by omega) hp h4))

lemma p14_fact (m : ℕ) (hm : 1 ≤ m) :
    Nat.factorial (m + 1) = (m + 1) * m * Nat.factorial (m - 1) := by
  obtain ⟨j, rfl⟩ : ∃ j, m = j + 1 := ⟨m - 1, by omega⟩
  simp only [Nat.factorial_succ, Nat.add_sub_cancel]
  ring

lemma p14_iter (n : ℕ) :
    iteratedDeriv n (fun x : ℝ => (1 - x + x ^ 2) * Real.exp x)
      = fun x : ℝ => (x ^ 2 + (2 * (n : ℝ) - 1) * x + ((n : ℝ) - 1) ^ 2) * Real.exp x := by
  induction n with
  | zero =>
    funext x
    simp only [iteratedDeriv_zero, Nat.cast_zero]
    ring
  | succ n ih =>
    have key : ∀ x : ℝ, HasDerivAt
        (fun y : ℝ => (y ^ 2 + (2 * (n : ℝ) - 1) * y + ((n : ℝ) - 1) ^ 2) * Real.exp y)
        ((2 * x + (2 * (n : ℝ) - 1)) * Real.exp x
          + (x ^ 2 + (2 * (n : ℝ) - 1) * x + ((n : ℝ) - 1) ^ 2) * Real.exp x) x := by
      intro x
      have h1 : HasDerivAt (fun y : ℝ => y ^ 2 + (2 * (n : ℝ) - 1) * y + ((n : ℝ) - 1) ^ 2)
          (2 * x + (2 * (n : ℝ) - 1)) x := by
        have h := ((hasDerivAt_pow 2 x).add
          ((hasDerivAt_id x).const_mul (2 * (n : ℝ) - 1))).add_const (((n : ℝ) - 1) ^ 2)
        convert h using 1 <;> norm_num
      exact h1.mul (Real.hasDerivAt_exp x)
    rw [iteratedDeriv_succ, ih]
    funext x
    rw [(key x).deriv]
    push_cast
    ring

lemma p14_num (m : ℕ) (hm : 1 ≤ m) :
    (((m : ℚ)) ^ 2 / ((Nat.factorial (m + 1) : ℕ) : ℚ)).num = 1 ∨
      _root_.Prime (((m : ℚ)) ^ 2 / ((Nat.factorial (m + 1) : ℕ) : ℚ)).num.natAbs := by
  have hm0 : (m : ℚ) ≠ 0 := Nat.cast_ne_zero.mpr (by omega)
  have hm1 : (m : ℚ) + 1 ≠ 0 := by positivity
  have hF0 : ((Nat.factorial (m - 1) : ℕ) : ℚ) ≠ 0 :=
    Nat.cast_ne_zero.mpr (Nat.factorial_ne_zero _)
  rcases p14_trich m hm with hp | hdiv | h4
  · right
    have hfac : ((Nat.factorial (m + 1) : ℕ) : ℚ)
        = ((m : ℚ) + 1) * (m : ℚ) * ((Nat.factorial (m - 1) : ℕ) : ℚ) := by
      rw [p14_fact m hm]
      push_cast
      ring
    have hcop : Nat.Coprime m ((m + 1) * Nat.factorial (m - 1)) := by
      rw [Nat.Prime.coprime_iff_not_dvd hp]
      intro hdvd
      rcases (Nat.Prime.dvd_mul hp).mp hdvd with h | h
      · have h1 : m ∣ 1 := (Nat.dvd_add_right (dvd_refl m)).mp h
        have h2 := Nat.le_of_dvd one_pos h1
        have h3 := hp.two_le
        omega
      · rw [Nat.Prime.dvd_factorial hp] at h
        omega
    have hq : (((m : ℚ)) ^ 2 / ((Nat.factorial (m + 1) : ℕ) : ℚ))
        = (((m : ℤ) : ℚ)) / ((((m + 1) * Nat.factorial (m - 1) : ℕ) : ℤ) : ℚ) := by
      rw [hfac]
      push_cast
      rw [div_eq_div_iff (mul_ne_zero (mul_ne_zero hm1 hm0) hF0) (mul_ne_zero hm1 hF0)]
      ring
    have hpos' : 0 < (m + 1) * Nat.factorial (m - 1) :=
      Nat.mul_pos (by omega) (Nat.factorial_pos _)
    have hpos : (0 : ℤ) < (((m + 1) * Nat.factorial (m - 1) : ℕ) : ℤ) := by
      exact_mod_cast hpos'
    have hnum : (((((m : ℤ)) : ℚ)) / ((((m + 1) * Nat.factorial (m - 1) : ℕ) : ℤ) : ℚ)).num
        = (m : ℤ) :=
      Rat.num_div_eq_of_coprime hpos hcop
    rw [hq, hnum]
    exact Nat.prime_iff.mp hp
  · left
    obtain ⟨G, hG⟩ := hdiv
    have hGpos : 0 < G := by
      rcases Nat.eq_zero_or_pos G with h0 | h0
      · rw [h0, mul_zero] at hG
        exact absurd hG (Nat.factorial_ne_zero _)
      · exact h0
    have hG0 : (G : ℚ) ≠ 0 := Nat.cast_ne_zero.mpr (by omega)
    have hfac2 : ((Nat.factorial (m + 1) : ℕ) : ℚ)
        = ((m : ℚ) + 1) * (m : ℚ) * ((m : ℚ) * (G : ℚ)) := by
      rw [p14_fact m hm, hG]
      push_cast
      ring
    have hq : (((m : ℚ)) ^ 2 / ((Nat.factorial (m + 1) : ℕ) : ℚ))
        = (((1 : ℤ) : ℚ)) / ((((m + 1) * G : ℕ) : ℤ) : ℚ) := by
      rw [hfac2]
      push_cast
      rw [div_eq_div_iff (mul_ne_zero (mul_ne_zero hm1 hm0) (mul_ne_zero hm0 hG0))
        (mul_ne_zero hm1 hG0)]
      ring
    have hpos' : 0 < (m + 1) * G := Nat.mul_pos (by omega) hGpos
    have hpos : (0 : ℤ) < (((m + 1) * G : ℕ) : ℤ) := by exact_mod_cast hpos'
    have hnum : (((((1 : ℤ)) : ℚ)) / ((((m + 1) * G : ℕ) : ℤ) : ℚ)).num = 1 :=
      Rat.num_div_eq_of_coprime hpos (Nat.coprime_one_left _)
    rw [hq, hnum]
  · right
    subst h4
    have hq4 : (((4 : ℕ)) : ℚ) ^ 2 / ((Nat.factorial (4 + 1) : ℕ) : ℚ)
        = (((2 : ℤ)) : ℚ) / (((15 : ℤ)) : ℚ) := by
      norm_num [Nat.factorial]
    have hnum4 : (((((2 : ℤ)) : ℚ)) / (((15 : ℤ)) : ℚ)).num = 2 :=
      Rat.num_div_eq_of_coprime (by norm_num) (by norm_num)
    rw [hq4, hnum4]
    exact Nat.prime_iff.mp Nat.prime_two

theorem putnam_2014_a1 (f : ℝ → ℝ)
(hf : ∀ x : ℝ, f x = (1 - x + x ^ 2) * Real.exp x)
(hfdiff : ContDiff ℝ ⊤ f)
(c : ℕ → ℝ)
(hc : ∀ k : ℕ, c k = taylorCoeffWithin f k Set.univ 0)
: ∀ k : ℕ, c k ≠ 0 → ∃ q : ℚ, c k = q ∧ (q.num = 1 ∨ Prime q.num.natAbs) := by
  have hfeq : f = fun x : ℝ => (1 - x + x ^ 2) * Real.exp x := funext hf
  have hc' : ∀ k : ℕ, c k = ((k : ℝ) - 1) ^ 2 / ((Nat.factorial k : ℕ) : ℝ) := by
    intro k
    rw [hc k, hfeq]
    simp only [taylorCoeffWithin, iteratedDerivWithin_univ, p14_iter, smul_eq_mul,
      Real.exp_zero]
    ring
  intro k hk
  rcases Nat.eq_zero_or_pos k with hk0 | hkpos
  · subst hk0
    refine ⟨1, ?_, Or.inl ?_⟩
    · rw [hc' 0]
      norm_num
    · first
        | rfl
        | norm_num
        | simp
  · obtain ⟨m, rfl⟩ : ∃ m, k = m + 1 := ⟨k - 1, by omega⟩
    rcases Nat.eq_zero_or_pos m with hm0 | hm
    · exfalso
      apply hk
      subst hm0
      rw [hc' (0 + 1)]
      norm_num
    · refine ⟨((m : ℚ)) ^ 2 / ((Nat.factorial (m + 1) : ℕ) : ℚ), ?_, p14_num m hm⟩
      rw [hc' (m + 1)]
      push_cast <;> ring

#print axioms putnam_2014_a1
