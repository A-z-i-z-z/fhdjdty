import Mathlib

open Finset

open MvPolynomial

theorem putnam_2003_a2 (n : ℕ)
    (hn : 0 < n)
    (a b : Fin n → ℝ)
    (abnneg : ∀ i, a i ≥ 0 ∧ b i ≥ 0) :
    (∏ i, a i) ^ ((1 : ℝ) / n) +
    (∏ i, b i) ^ ((1 : ℝ) / n) ≤
    (∏ i, (a i + b i)) ^ ((1 : ℝ) / n) := by
  have hnr : (0:ℝ) < n := by exact_mod_cast hn
  have hne : (n:ℝ) ≠ 0 := hnr.ne'
  have hr : (1:ℝ) / n ≠ 0 := one_div_ne_zero hne
  by_cases h0 : ∃ i, a i + b i = 0
  · obtain ⟨i, hi⟩ := h0
    have ha : a i = 0 := by linarith [(abnneg i).1, (abnneg i).2]
    have hb : b i = 0 := by linarith [(abnneg i).1, (abnneg i).2]
    have hA : ∏ j, a j = 0 := Finset.prod_eq_zero (Finset.mem_univ i) ha
    have hB : ∏ j, b j = 0 := Finset.prod_eq_zero (Finset.mem_univ i) hb
    have hC : ∏ j, (a j + b j) = 0 := Finset.prod_eq_zero (Finset.mem_univ i) hi
    have h1 : (∏ j, a j) ^ ((1:ℝ) / n) = 0 := by
      rw [hA]
      exact Real.zero_rpow hr
    have h2 : (∏ j, b j) ^ ((1:ℝ) / n) = 0 := by
      rw [hB]
      exact Real.zero_rpow hr
    have h3 : (∏ j, (a j + b j)) ^ ((1:ℝ) / n) = 0 := by
      rw [hC]
      exact Real.zero_rpow hr
    rw [h1, h2, h3]
    norm_num
  · push_neg at h0
    have hpos : ∀ i, 0 < a i + b i := fun i =>
      lt_of_le_of_ne (by linarith [(abnneg i).1, (abnneg i).2]) (fun h => h0 i h.symm)
    have hP : 0 ≤ ∏ i, a i := Finset.prod_nonneg (fun i _ => (abnneg i).1)
    have hQ : 0 ≤ ∏ i, b i := Finset.prod_nonneg (fun i _ => (abnneg i).2)
    have hCpos : 0 < ∏ i, (a i + b i) := Finset.prod_pos (fun i _ => hpos i)
    have hCr : 0 < (∏ i, (a i + b i)) ^ ((1:ℝ) / n) := Real.rpow_pos_of_pos hCpos _
    have hCr' : (∏ i, (a i + b i)) ^ ((1:ℝ) / n) ≠ 0 := hCr.ne'
    have hxy : ∀ i, a i / (a i + b i) + b i / (a i + b i) = 1 := by
      intro i
      rw [← add_div, div_self (hpos i).ne']
    have hS : ∑ i, a i / (a i + b i) + ∑ i, b i / (a i + b i) = (n:ℝ) := by
      rw [← Finset.sum_add_distrib]
      simp only [hxy, Finset.sum_const, Finset.card_univ, Fintype.card_fin, nsmul_eq_mul, mul_one]
    have amgm : ∀ z : Fin n → ℝ, (∀ i, 0 ≤ z i) →
        ∏ i, z i ^ ((1:ℝ) / n) ≤ ∑ i, (1 / (n:ℝ)) * z i := by
      intro z hz
      apply Real.geom_mean_le_arith_mean_weighted
      · intro i _
        positivity
      · simp only [Finset.sum_const, Finset.card_univ, Fintype.card_fin, nsmul_eq_mul]
        field_simp
      · intro i _
        exact hz i
    have hx : ∏ i, (a i / (a i + b i)) ^ ((1:ℝ) / n)
        ≤ ∑ i, (1 / (n:ℝ)) * (a i / (a i + b i)) :=
      amgm (fun i => a i / (a i + b i)) (fun i => div_nonneg (abnneg i).1 (hpos i).le)
    have hy : ∏ i, (b i / (a i + b i)) ^ ((1:ℝ) / n)
        ≤ ∑ i, (1 / (n:ℝ)) * (b i / (a i + b i)) :=
      amgm (fun i => b i / (a i + b i)) (fun i => div_nonneg (abnneg i).2 (hpos i).le)
    rw [← Finset.mul_sum] at hx hy
    have hdiv1 : ∏ i, (a i / (a i + b i)) ^ ((1:ℝ) / n)
        = (∏ i, a i) ^ ((1:ℝ) / n) / (∏ i, (a i + b i)) ^ ((1:ℝ) / n) := by
      have h := Real.finset_prod_rpow Finset.univ (fun i => a i / (a i + b i))
        (fun i _ => div_nonneg (abnneg i).1 (hpos i).le) ((1:ℝ) / n)
      rw [Finset.prod_div_distrib, Real.div_rpow hP hCpos.le] at h
      exact h
    have hdiv2 : ∏ i, (b i / (a i + b i)) ^ ((1:ℝ) / n)
        = (∏ i, b i) ^ ((1:ℝ) / n) / (∏ i, (a i + b i)) ^ ((1:ℝ) / n) := by
      have h := Real.finset_prod_rpow Finset.univ (fun i => b i / (a i + b i))
        (fun i _ => div_nonneg (abnneg i).2 (hpos i).le) ((1:ℝ) / n)
      rw [Finset.prod_div_distrib, Real.div_rpow hQ hCpos.le] at h
      exact h
    rw [hdiv1] at hx
    rw [hdiv2] at hy
    have hsum : (∏ i, a i) ^ ((1:ℝ) / n) / (∏ i, (a i + b i)) ^ ((1:ℝ) / n)
        + (∏ i, b i) ^ ((1:ℝ) / n) / (∏ i, (a i + b i)) ^ ((1:ℝ) / n) ≤ 1 := by
      have h1 : (1 / (n:ℝ)) * ∑ i, a i / (a i + b i)
          + (1 / (n:ℝ)) * ∑ i, b i / (a i + b i) = 1 := by
        rw [← mul_add, hS]
        field_simp
      linarith
    have key : (∏ i, a i) ^ ((1:ℝ) / n) + (∏ i, b i) ^ ((1:ℝ) / n)
        = ((∏ i, a i) ^ ((1:ℝ) / n) / (∏ i, (a i + b i)) ^ ((1:ℝ) / n)
          + (∏ i, b i) ^ ((1:ℝ) / n) / (∏ i, (a i + b i)) ^ ((1:ℝ) / n))
          * (∏ i, (a i + b i)) ^ ((1:ℝ) / n) := by
      field_simp
    have h2 := mul_le_mul_of_nonneg_right hsum hCr.le
    linarith [h2, key]

#print axioms putnam_2003_a2
