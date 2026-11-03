import Mathlib

open Finset

open Filter Topology Set

theorem putnam_2010_b6 (n : ℕ)
(npos : n ≥ 1)
(A : Matrix (Fin n) (Fin n) ℝ)
(Apow : ℕ → Matrix (Fin n) (Fin n) ℝ)
(hApow : ∀ k > 0, Apow k = (fun i j : Fin n => (A i j) ^ k))
: (∀ k ∈ Set.Icc 1 (n + 1), A ^ k = Apow k) → (∀ k ≥ 1, A ^ k = Apow k) := by
  intro hlow
  have H0 : ∀ k, 1 ≤ k → k ≤ n + 1 → ∀ i j, (A ^ k) i j = A i j ^ k := by
    intro k hk1 hkn i j
    have hk : k ∈ Set.Icc 1 (n + 1) := ⟨hk1, hkn⟩
    simp only [hlow k hk, hApow k (by omega)]
  have hq : Polynomial.aeval A A.charpoly = 0 := Matrix.aeval_self_charpoly A
  have hdeg : A.charpoly.natDegree = n := by
    rw [Matrix.charpoly_natDegree_eq_dim]
    simp
  have hcoeff_top : A.charpoly.coeff n = 1 := by
    have h := (Matrix.charpoly_monic A).coeff_natDegree
    rw [hdeg] at h
    exact h
  have key : ∀ (r : Polynomial ℝ) (N : ℕ), r.natDegree < N → r.coeff 0 = 0 →
      (∀ l < N, 1 ≤ l → ∀ i j, (A ^ l) i j = A i j ^ l) →
      ∀ i j, (Polynomial.aeval A r) i j = Polynomial.eval (A i j) r := by
    intro r N hN hr0 hl i j
    rw [Polynomial.aeval_eq_sum_range' hN, Polynomial.eval_eq_sum_range' hN]
    simp only [Matrix.sum_apply, Matrix.smul_apply, smul_eq_mul]
    apply Finset.sum_congr rfl
    intro l hl'
    by_cases h0 : l = 0
    · subst h0
      simp [hr0]
    · rw [hl l (Finset.mem_range.mp hl') (by omega) i j]
  have hdegXq : (Polynomial.X * A.charpoly).natDegree < n + 2 := by
    have h1 : (Polynomial.X * A.charpoly).natDegree ≤
        (Polynomial.X : Polynomial ℝ).natDegree + A.charpoly.natDegree :=
      Polynomial.natDegree_mul_le
    have h2 : (Polynomial.X : Polynomial ℝ).natDegree ≤ 1 := Polynomial.natDegree_X_le
    omega
  have hXq : Polynomial.aeval A (Polynomial.X * A.charpoly) = 0 := by
    rw [map_mul, Polynomial.aeval_X, hq, mul_zero]
  have hscal : ∀ i j, A i j * Polynomial.eval (A i j) A.charpoly = 0 := by
    intro i j
    have h := key (Polynomial.X * A.charpoly) (n + 2) hdegXq
      (by simp) (fun l hl hl1 i j => H0 l hl1 (by omega) i j) i j
    rw [hXq] at h
    simp only [Matrix.zero_apply, Polynomial.eval_mul, Polynomial.eval_X] at h
    exact h.symm
  have hpowzero : ∀ a e : ℝ, a * e = 0 → ∀ m, 1 ≤ m → a ^ m * e = 0 := by
    intro a e h m hm
    obtain ⟨m', rfl⟩ : ∃ m', m = m' + 1 := ⟨m - 1, by omega⟩
    have : a ^ (m' + 1) * e = a ^ m' * (a * e) := by ring
    rw [this, h, mul_zero]
  have main : ∀ k, 1 ≤ k → ∀ i j, (A ^ k) i j = A i j ^ k := by
    intro k
    induction k using Nat.strong_induction_on with
    | _ k ih =>
      intro hk1 i j
      by_cases hkn : k ≤ n + 1
      · exact H0 k hk1 hkn i j
      · obtain ⟨p, rfl⟩ : ∃ p, k = p + n := ⟨k - n, by omega⟩
        have hp : 1 ≤ p := by omega
        have hdeg1 : (Polynomial.X ^ p * A.charpoly).natDegree ≤ p + n := by
          have h1 : (Polynomial.X ^ p * A.charpoly).natDegree ≤
              (Polynomial.X ^ p : Polynomial ℝ).natDegree + A.charpoly.natDegree :=
            Polynomial.natDegree_mul_le
          have h2 : (Polynomial.X ^ p : Polynomial ℝ).natDegree = p := by simp
          omega
        have hdlt : (Polynomial.X ^ p * A.charpoly).natDegree < p + n + 1 := by omega
        have hAr : Polynomial.aeval A (Polynomial.X ^ p * A.charpoly) = 0 := by
          rw [map_mul, map_pow, Polynomial.aeval_X, hq, mul_zero]
        have hc1 : (Polynomial.X ^ p * A.charpoly).coeff (p + n) = 1 := by
          rw [Polynomial.coeff_X_pow_mul', if_pos (show p ≤ p + n by omega)]
          simpa using hcoeff_top
        have hc0 : (Polynomial.X ^ p * A.charpoly).coeff 0 = 0 := by
          rw [Polynomial.coeff_X_pow_mul', if_neg (show ¬ (p ≤ 0) by omega)]
        have E2 : (Polynomial.aeval A (Polynomial.X ^ p * A.charpoly)) i j = 0 := by
          rw [hAr]
          simp
        have E3 : Polynomial.eval (A i j) (Polynomial.X ^ p * A.charpoly) = 0 := by
          rw [Polynomial.eval_mul, Polynomial.eval_pow, Polynomial.eval_X]
          exact hpowzero _ _ (hscal i j) p hp
        rw [Polynomial.aeval_eq_sum_range' hdlt] at E2
        simp only [Matrix.sum_apply, Matrix.smul_apply, smul_eq_mul,
          Finset.sum_range_succ] at E2
        rw [Polynomial.eval_eq_sum_range' hdlt] at E3
        simp only [Finset.sum_range_succ] at E3
        have hsum : ∑ x ∈ Finset.range (p + n),
              (Polynomial.X ^ p * A.charpoly).coeff x * (A ^ x) i j
            = ∑ x ∈ Finset.range (p + n),
              (Polynomial.X ^ p * A.charpoly).coeff x * A i j ^ x := by
          apply Finset.sum_congr rfl
          intro x hx
          rw [Finset.mem_range] at hx
          by_cases hx0 : x = 0
          · subst hx0
            simp [hc0]
          · rw [ih x hx (by omega) i j]
        simp only [hc1, one_mul] at E2 E3
        linarith [hsum]
  intro k hk1
  rw [hApow k (by omega)]
  funext i j
  exact main k hk1 i j

#print axioms putnam_2010_b6
