import Mathlib

open Finset

open Set Nat Function

theorem putnam_2007_b5 (k : ℕ) (kpos : k > 0) :
(∃ P : Finset.range k → Polynomial ℝ, ∀ n : ℤ, ⌊(n : ℝ) / k⌋ ^ k = ∑ i : Finset.range k, (P i).eval (n : ℝ) * ⌊(n : ℝ) / k⌋ ^ (i : ℕ)) := by
  have hk0 : (k:ℝ) ≠ 0 := Nat.cast_ne_zero.mpr (by omega)
  have hkR : (0:ℝ) < (k:ℝ) := by exact_mod_cast kpos
  obtain ⟨F, hF⟩ : ∃ F : Polynomial (Polynomial ℝ), F = ∏ j ∈ Finset.range k,
      (Polynomial.C (Polynomial.C (-(k:ℝ))) * Polynomial.X
        + Polynomial.C (Polynomial.X - Polynomial.C (j:ℝ))) := ⟨_, rfl⟩
  have hfac_deg : ∀ j : ℕ, (Polynomial.C (Polynomial.C (-(k:ℝ))) * Polynomial.X
        + Polynomial.C (Polynomial.X - Polynomial.C (j:ℝ))).natDegree = 1 := by
    intro j
    apply Polynomial.natDegree_linear
    rw [Ne, Polynomial.C_eq_zero, neg_eq_zero]
    exact hk0
  have hfac_lc : ∀ j : ℕ, (Polynomial.C (Polynomial.C (-(k:ℝ))) * Polynomial.X
        + Polynomial.C (Polynomial.X - Polynomial.C (j:ℝ))).leadingCoeff
        = Polynomial.C (-(k:ℝ)) := by
    intro j
    rw [Polynomial.leadingCoeff, hfac_deg j]
    simp
  have hF_ne : ∀ j ∈ Finset.range k, (Polynomial.C (Polynomial.C (-(k:ℝ))) * Polynomial.X
        + Polynomial.C (Polynomial.X - Polynomial.C (j:ℝ))) ≠ 0 := by
    intro j _ h
    have h' := hfac_deg j
    rw [h] at h'
    simp at h'
  have hFdeg : F.natDegree = k := by
    rw [hF, Polynomial.natDegree_prod _ _ hF_ne]
    simp only [hfac_deg, Finset.sum_const, Finset.card_range, smul_eq_mul, nsmul_eq_mul,
      mul_one, Nat.cast_id]
  have hFlc : F.leadingCoeff = Polynomial.C ((-(k:ℝ))^k) := by
    rw [hF, Polynomial.leadingCoeff_prod]
    simp only [hfac_lc, Finset.prod_const, Finset.card_range]
    rw [Polynomial.C_pow]
  have hcoeffk : F.coeff k = Polynomial.C ((-(k:ℝ))^k) := by
    have h : F.coeff F.natDegree = F.leadingCoeff := Polynomial.coeff_natDegree
    rw [hFdeg, hFlc] at h
    exact h
  have key : ∀ n m : ℝ, ∑ i ∈ Finset.range (k+1), (F.coeff i).eval n * m ^ i
      = ∏ j ∈ Finset.range k, (n - (j:ℝ) - (k:ℝ) * m) := by
    intro n m
    have hdeg_le : (F.map (Polynomial.evalRingHom n)).natDegree ≤ F.natDegree := by
      first
        | exact Polynomial.natDegree_map_le
        | exact Polynomial.natDegree_map_le _ _
    have hlt : (F.map (Polynomial.evalRingHom n)).natDegree < k + 1 := by
      rw [hFdeg] at hdeg_le
      omega
    have h1 : (F.map (Polynomial.evalRingHom n)).eval m
        = ∏ j ∈ Finset.range k, (n - (j:ℝ) - (k:ℝ) * m) := by
      rw [hF, Polynomial.eval_map, Polynomial.eval₂_finset_prod]
      apply Finset.prod_congr rfl
      intro j _
      simp <;> ring
    rw [← h1, Polynomial.eval_eq_sum_range' hlt]
    apply Finset.sum_congr rfl
    intro i _
    simp [Polynomial.coeff_map]
  have hc : (-(k:ℝ))^k ≠ 0 := pow_ne_zero _ (neg_ne_zero.mpr hk0)
  have hinv : (1 / (-(k:ℝ))^k) * (-(k:ℝ))^k = 1 := by
    first
      | exact one_div_mul_cancel hc
      | field_simp
  refine ⟨fun i => Polynomial.C (-(1 / (-(k:ℝ))^k)) * F.coeff (i:ℕ), ?_⟩
  intro n
  obtain ⟨m, hm⟩ : ∃ m : ℤ, m = ⌊(n:ℝ) / k⌋ := ⟨_, rfl⟩
  rw [← hm]
  have h1 : (m:ℝ) ≤ (n:ℝ) / k := by
    rw [hm]
    exact Int.floor_le _
  have h2 : (n:ℝ) / k < (m:ℝ) + 1 := by
    rw [hm]
    exact Int.lt_floor_add_one _
  have hz1 : (m:ℝ) * k ≤ n := by
    first
      | exact (le_div_iff₀ hkR).mp h1
      | exact (le_div_iff hkR).mp h1
  have hz2 : (n:ℝ) < ((m:ℝ) + 1) * k := by
    first
      | exact (div_lt_iff₀ hkR).mp h2
      | exact (div_lt_iff hkR).mp h2
  have hzi1 : m * (k:ℤ) ≤ n := by exact_mod_cast hz1
  have hzi2 : (n:ℤ) < (m + 1) * (k:ℤ) := by exact_mod_cast hz2
  have hnn : (0:ℤ) ≤ n - m * (k:ℤ) := by linarith
  obtain ⟨j, hj⟩ := Int.eq_ofNat_of_zero_le hnn
  have e : (m + 1) * (k:ℤ) = m * (k:ℤ) + (k:ℤ) := by ring
  have hjk : j < k := by
    have hlt' : (j:ℤ) < (k:ℤ) := by linarith
    exact_mod_cast hlt'
  have hjr : (j:ℝ) = (n:ℝ) - (k:ℝ) * (m:ℝ) := by
    have h := congrArg (fun x : ℤ => (x:ℝ)) hj
    push_cast at h
    linarith [mul_comm (m:ℝ) (k:ℝ)]
  have hzero : ∏ j' ∈ Finset.range k, ((n:ℝ) - (j':ℝ) - (k:ℝ) * (m:ℝ)) = 0 := by
    refine Finset.prod_eq_zero (Finset.mem_range.mpr hjk) ?_
    show (n:ℝ) - (j:ℝ) - (k:ℝ) * (m:ℝ) = 0
    linarith [hjr]
  have hk1 := key (n:ℝ) (m:ℝ)
  rw [hzero, Finset.sum_range_succ, hcoeffk, Polynomial.eval_C] at hk1
  have hsplit : (-(k:ℝ))^k * (m:ℝ)^k
      = -(∑ i ∈ Finset.range k, (F.coeff i).eval (n:ℝ) * (m:ℝ)^i) := by
    linarith
  have hsum_eq : ∑ i ∈ Finset.range k, -(1 / (-(k:ℝ))^k) * (F.coeff i).eval (n:ℝ) * (m:ℝ)^i
      = -(1 / (-(k:ℝ))^k) * ∑ i ∈ Finset.range k, (F.coeff i).eval (n:ℝ) * (m:ℝ)^i := by
    rw [Finset.mul_sum]
    apply Finset.sum_congr rfl
    intro i _
    ring
  have hmain : (m:ℝ)^k
      = ∑ i ∈ Finset.range k, -(1 / (-(k:ℝ))^k) * (F.coeff i).eval (n:ℝ) * (m:ℝ)^i := by
    rw [hsum_eq]
    calc (m:ℝ)^k = (1 / (-(k:ℝ))^k) * ((-(k:ℝ))^k * (m:ℝ)^k) := by
          rw [← mul_assoc, hinv, one_mul]
      _ = (1 / (-(k:ℝ))^k) * (-(∑ i ∈ Finset.range k, (F.coeff i).eval (n:ℝ) * (m:ℝ)^i)) := by
          rw [hsplit]
      _ = -(1 / (-(k:ℝ))^k) * ∑ i ∈ Finset.range k, (F.coeff i).eval (n:ℝ) * (m:ℝ)^i := by
          ring
  simp only [Polynomial.eval_mul, Polynomial.eval_C]
  refine Eq.trans ?_ (Finset.sum_coe_sort (Finset.range k)
    (fun j : ℕ => -(1 / (-(k:ℝ))^k) * (F.coeff j).eval (n:ℝ) * (m:ℝ)^j)).symm
  first
    | exact hmain
    | exact_mod_cast hmain

#print axioms putnam_2007_b5
