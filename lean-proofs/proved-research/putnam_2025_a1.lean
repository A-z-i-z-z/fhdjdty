import Mathlib

open Finset

theorem putnam_helper_step (m n : ℕ → ℕ)
    (hm : ∀ k : ℕ, 0 < m k) (hn : ∀ k : ℕ, 0 < n k)
    (h_coprime : ∀ k : ℕ, 0 < k → Nat.Coprime (m k) (n k))
    (h_recurrence : ∀ k : ℕ,
      (m (k + 1) : ℚ) / (n (k + 1) : ℚ) = (2 * (m k : ℚ) + 1) / (2 * (n k : ℚ) + 1))
    (k : ℕ) :
    ∃ t : ℕ, 2 * m k + 1 = m (k + 1) * t ∧ 2 * n k + 1 = n (k + 1) * t := by
  have hn1 : (n (k + 1) : ℚ) ≠ 0 := by exact_mod_cast (hn (k + 1)).ne'
  have hB : (2 * (n k : ℚ) + 1) ≠ 0 := by positivity
  have hq := h_recurrence k
  have hc : (m (k + 1) : ℚ) * (2 * (n k : ℚ) + 1) = (2 * (m k : ℚ) + 1) * (n (k + 1) : ℚ) := by
    first
      | (rw [div_eq_div_iff hn1 hB] at hq; linarith)
      | (field_simp at hq; linarith)
  have hc' : m (k + 1) * (2 * n k + 1) = (2 * m k + 1) * n (k + 1) := by
    exact_mod_cast hc
  have hcop : Nat.Coprime (m (k + 1)) (n (k + 1)) := h_coprime (k + 1) (Nat.succ_pos k)
  have hdvd : m (k + 1) ∣ (2 * m k + 1) * n (k + 1) := ⟨2 * n k + 1, by rw [← hc']⟩
  have hdvd' : m (k + 1) ∣ 2 * m k + 1 := by
    first
      | exact hcop.dvd_of_dvd_mul_right hdvd
      | exact hcop.dvd_of_dvd_mul_left hdvd
  obtain ⟨t, ht⟩ := hdvd'
  refine ⟨t, ht, ?_⟩
  apply Nat.eq_of_mul_eq_mul_left (hm (k + 1))
  rw [hc', ht]
  ring

theorem putnam_helper_min (P : ℕ → Prop) (h : ∃ w, P w) :
    ∃ w, P w ∧ ∀ v, P v → w ≤ v := by
  classical
  exact ⟨Nat.find h, Nat.find_spec h, fun v hv => Nat.find_min' h hv⟩

theorem putnam_2025_a1 (m n : ℕ → ℕ)
  (hm : ∀ k : ℕ, 0 < m k)
  (hn : ∀ k : ℕ, 0 < n k)
  (h_distinct : m 0 ≠ n 0)
  (h_coprime : ∀ k : ℕ, 0 < k → Nat.Coprime (m k) (n k))
  (h_recurrence : ∀ k : ℕ,
    (m (k + 1) : ℚ) / (n (k + 1) : ℚ) = (2 * (m k : ℚ) + 1) / (2 * (n k : ℚ) + 1)) :
  {k : ℕ | ¬Nat.Coprime (2 * m k + 1) (2 * n k + 1)}.Finite := by
  choose t ht using fun k => putnam_helper_step m n hm hn h_coprime h_recurrence k
  have hD : ∀ k : ℕ, (t k : ℤ) * ((m (k + 1) : ℤ) - n (k + 1)) = 2 * ((m k : ℤ) - n k) := by
    intro k
    obtain ⟨h1, h2⟩ := ht k
    have h1' : (2 * (m k : ℤ) + 1) = (m (k + 1) : ℤ) * (t k : ℤ) := by exact_mod_cast h1
    have h2' : (2 * (n k : ℤ) + 1) = (n (k + 1) : ℤ) * (t k : ℤ) := by exact_mod_cast h2
    linear_combination h2' - h1'
  have hDne : ∀ k : ℕ, ((m k : ℤ) - n k) ≠ 0 := by
    intro k
    induction k with
    | zero =>
      intro h
      apply h_distinct
      omega
    | succ k ih =>
      intro h
      have e := hD k
      rw [h, mul_zero] at e
      exact ih (by linarith)
  have hdiv : ∀ k : ℕ, ∃ E : ℤ, (m k : ℤ) - n k = 2 ^ k * E := by
    intro k
    induction k with
    | zero => exact ⟨(m 0 : ℤ) - n 0, by simp⟩
    | succ k ih =>
      obtain ⟨E, hE⟩ := ih
      obtain ⟨h1, _⟩ := ht k
      have h1' : (2 * (m k : ℤ) + 1) = (m (k + 1) : ℤ) * (t k : ℤ) := by exact_mod_cast h1
      have hodd : IsCoprime (2 : ℤ) (t k : ℤ) := ⟨-(m k : ℤ), (m (k + 1) : ℤ), by linarith⟩
      have hcop2 : IsCoprime ((2 : ℤ) ^ (k + 1)) (t k : ℤ) := IsCoprime.pow_left hodd
      have hdvd : (2 : ℤ) ^ (k + 1) ∣ (t k : ℤ) * ((m (k + 1) : ℤ) - n (k + 1)) :=
        ⟨E, by rw [hD k, hE]; ring⟩
      have hX : (2 : ℤ) ^ (k + 1) ∣ (m (k + 1) : ℤ) - n (k + 1) := by
        first
          | exact hcop2.dvd_of_dvd_mul_left hdvd
          | exact hcop2.dvd_of_dvd_mul_right hdvd
      obtain ⟨E', hE'⟩ := hX
      exact ⟨E', hE'⟩
  have hex : ∃ w : ℕ, ∃ k : ℕ, ∃ E : ℤ, ((m k : ℤ) - n k) = 2 ^ k * E ∧ E.natAbs = w := by
    obtain ⟨E, hE⟩ := hdiv 0
    exact ⟨E.natAbs, 0, E, hE, rfl⟩
  obtain ⟨w0, ⟨k0, E0, hE0, hw0⟩, hmin⟩ :=
    putnam_helper_min (fun w => ∃ k : ℕ, ∃ E : ℤ, ((m k : ℤ) - n k) = 2 ^ k * E ∧ E.natAbs = w) hex
  have hstep : ∀ k : ℕ, ∀ E : ℤ, ((m k : ℤ) - n k) = 2 ^ k * E → E.natAbs = w0 →
      t k = 1 ∧ ∃ E' : ℤ, ((m (k + 1) : ℤ) - n (k + 1)) = 2 ^ (k + 1) * E' ∧ E'.natAbs = w0 := by
    intro k E hE hw
    obtain ⟨E', hE'⟩ := hdiv (k + 1)
    have key : (t k : ℤ) * E' = E := by
      have e := hD k
      rw [hE', hE] at e
      have h2 : (2 : ℤ) ^ (k + 1) * ((t k : ℤ) * E') = (2 : ℤ) ^ (k + 1) * E := by
        linear_combination e
      exact mul_left_cancel₀ (pow_ne_zero _ two_ne_zero) h2
    have hn2 : E.natAbs = t k * E'.natAbs := by
      rw [← key]
      simp [Int.natAbs_mul]
    have hmin' : w0 ≤ E'.natAbs := hmin E'.natAbs ⟨k + 1, E', hE', rfl⟩
    have hE0ne : E ≠ 0 := by
      rintro rfl
      exact hDne k (by rw [hE, mul_zero])
    have hw1 : 0 < w0 := by
      rw [← hw]
      exact Int.natAbs_pos.mpr hE0ne
    have htle : t k ≤ 1 := by
      by_contra hc
      have h2 : 2 * E'.natAbs ≤ t k * E'.natAbs := by
        apply Nat.mul_le_mul_right
        omega
      linarith
    have htge : 0 < t k := by
      rcases Nat.eq_zero_or_pos (t k) with h0 | h0
      · rw [h0, zero_mul] at hn2
        linarith
      · exact h0
    have ht1 : t k = 1 := by omega
    have ha : E'.natAbs = w0 := by
      rw [ht1, one_mul] at hn2
      linarith
    exact ⟨ht1, E', hE', ha⟩
  have hinv : ∀ j : ℕ, ∃ E : ℤ, ((m (k0 + j) : ℤ) - n (k0 + j)) = 2 ^ (k0 + j) * E ∧ E.natAbs = w0 := by
    intro j
    induction j with
    | zero => exact ⟨E0, hE0, hw0⟩
    | succ j ih =>
      obtain ⟨E, hE, hw⟩ := ih
      obtain ⟨_, E', hE', hw'⟩ := hstep (k0 + j) E hE hw
      refine ⟨E', ?_, hw'⟩
      rw [show k0 + (j + 1) = k0 + j + 1 by omega]
      exact hE'
  apply (Set.finite_lt_nat k0).subset
  intro k hk
  simp only [Set.mem_setOf_eq] at hk ⊢
  by_contra hlt
  push_neg at hlt
  obtain ⟨j, rfl⟩ := Nat.exists_eq_add_of_le hlt
  obtain ⟨E, hE, hw⟩ := hinv j
  have ht1 : t (k0 + j) = 1 := (hstep (k0 + j) E hE hw).1
  apply hk
  have h1 := (ht (k0 + j)).1
  have h2 := (ht (k0 + j)).2
  rw [ht1, mul_one] at h1 h2
  rw [h1, h2]
  exact h_coprime (k0 + j + 1) (by omega)

#print axioms putnam_2025_a1
