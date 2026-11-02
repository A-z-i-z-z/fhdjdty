import Mathlib

open Finset

theorem o_floor_sqrt (n : ℕ) : ⌊Real.sqrt n + Real.sqrt (n + 1)⌋ = ⌊Real.sqrt (4 * n + 2)⌋ := by
  have hn : (0:ℝ) ≤ (n:ℝ) := Nat.cast_nonneg n
  have hn1 : (0:ℝ) ≤ (n:ℝ) + 1 := by linarith
  have h4 : (0:ℝ) ≤ 4 * (n:ℝ) + 2 := by linarith
  have ha2 : Real.sqrt (n:ℝ) ^ 2 = (n:ℝ) := Real.sq_sqrt hn
  have hb2 : Real.sqrt ((n:ℝ) + 1) ^ 2 = (n:ℝ) + 1 := Real.sq_sqrt hn1
  have ht2 : Real.sqrt (4 * (n:ℝ) + 2) ^ 2 = 4 * (n:ℝ) + 2 := Real.sq_sqrt h4
  have ha0 : 0 ≤ Real.sqrt (n:ℝ) := Real.sqrt_nonneg _
  have hb0 : 0 ≤ Real.sqrt ((n:ℝ) + 1) := Real.sqrt_nonneg _
  have ht0 : 0 ≤ Real.sqrt (4 * (n:ℝ) + 2) := Real.sqrt_nonneg _
  set a := Real.sqrt (n:ℝ)
  set b := Real.sqrt ((n:ℝ) + 1)
  set t := Real.sqrt (4 * (n:ℝ) + 2)
  have hp : (a * b) ^ 2 = (n:ℝ) * ((n:ℝ) + 1) := by
    rw [mul_pow, ha2, hb2]
  have hp0 : 0 ≤ a * b := mul_nonneg ha0 hb0
  have hp_lo : (n:ℝ) ≤ a * b := by
    nlinarith [hp, hp0, hn]
  have hp_hi : a * b < (n:ℝ) + 1/2 := by
    nlinarith [hp, hp0, hn]
  have hs2 : (a + b) ^ 2 = 2 * (n:ℝ) + 1 + 2 * (a * b) := by
    linear_combination ha2 + hb2
  have hs_lo : 4 * (n:ℝ) + 1 ≤ (a + b) ^ 2 := by
    rw [hs2]; linarith
  have hs_hi : (a + b) ^ 2 < 4 * (n:ℝ) + 2 := by
    rw [hs2]; linarith
  have hab0 : 0 ≤ a + b := add_nonneg ha0 hb0
  have key : ∀ m : ℤ, m ^ 2 ≠ 4 * (n:ℤ) + 2 := by
    intro m h
    obtain ⟨k, rfl | rfl⟩ := Int.even_or_odd' m
    · have e : (2 * k) ^ 2 = 4 * (k ^ 2) := by ring
      rw [e] at h
      generalize k ^ 2 = q at h
      omega
    · have e : (2 * k + 1) ^ 2 = 4 * (k ^ 2 + k) + 1 := by ring
      rw [e] at h
      generalize k ^ 2 + k = q at h
      omega
  have hm0 : (0:ℤ) ≤ ⌊t⌋ := Int.floor_nonneg.mpr ht0
  have hmR0 : (0:ℝ) ≤ ((⌊t⌋:ℤ):ℝ) := by exact_mod_cast hm0
  have hm1 : t < ((⌊t⌋:ℤ):ℝ) + 1 := Int.lt_floor_add_one t
  have hm2 : ((⌊t⌋:ℤ):ℝ) ≤ t := Int.floor_le t
  have hsq : ((⌊t⌋:ℤ):ℝ) ^ 2 ≤ 4 * (n:ℝ) + 2 := by
    nlinarith [mul_nonneg (sub_nonneg.mpr hm2) (add_nonneg hmR0 ht0)]
  have hZ : (⌊t⌋ : ℤ) ^ 2 ≤ 4 * (n:ℤ) + 2 := by
    exact_mod_cast hsq
  have hZ2 : (⌊t⌋ : ℤ) ^ 2 ≤ 4 * (n:ℤ) + 1 := by
    have hlt := lt_of_le_of_ne hZ (key _)
    linarith
  have hR : ((⌊t⌋:ℤ):ℝ) ^ 2 ≤ 4 * (n:ℝ) + 1 := by
    exact_mod_cast hZ2
  have h1 : ((⌊t⌋:ℤ):ℝ) ≤ a + b := by
    by_contra hlt
    push_neg at hlt
    nlinarith [mul_pos (sub_pos.mpr hlt) (by linarith : (0:ℝ) < ((⌊t⌋:ℤ):ℝ) + (a + b))]
  have h2 : a + b < ((⌊t⌋:ℤ):ℝ) + 1 := by
    by_contra hge
    push_neg at hge
    nlinarith [mul_nonneg (sub_nonneg.mpr hge) (by linarith : (0:ℝ) ≤ (a + b) + (((⌊t⌋:ℤ):ℝ) + 1)),
      mul_pos (sub_pos.mpr hm1) (by linarith : (0:ℝ) < (((⌊t⌋:ℤ):ℝ) + 1) + t)]
  have hfl : ⌊a + b⌋ = ⌊t⌋ := Int.floor_eq_iff.mpr ⟨h1, h2⟩
  exact hfl

#print axioms o_floor_sqrt
