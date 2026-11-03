import Mathlib

open Finset

open Nat
abbrev putnam_2023_a1_solution : ℕ := 18

lemma putnam_key (n : ℕ) : ∃ D E : ℝ → ℝ,
    (∀ x : ℝ, HasDerivAt (fun x : ℝ => ∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * x)) (D x) x) ∧
    (∀ x : ℝ, HasDerivAt D (E x) x) ∧
    D 0 = 0 ∧
    E 0 = -∑ i ∈ Finset.Icc 1 n, (i : ℝ) ^ 2 ∧
    (∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * 0)) = 1 := by
  induction n with
  | zero =>
    have e : Finset.Icc 1 0 = (∅ : Finset ℕ) := by simp
    refine ⟨fun _ => 0, fun _ => 0, fun x => ?_, fun x => hasDerivAt_const x 0, rfl, ?_, ?_⟩
    · simp only [e, Finset.prod_empty]
      exact hasDerivAt_const x 1
    · simp [e]
    · simp [e]
  | succ n ih =>
    obtain ⟨D, E, hP, hD, hD0, hE0, hP0⟩ := ih
    obtain ⟨c, hc⟩ : ∃ c : ℝ, c = ((n + 1 : ℕ) : ℝ) := ⟨_, rfl⟩
    have hfun : (fun x : ℝ => ∏ i ∈ Finset.Icc 1 (n + 1), Real.cos ((i : ℝ) * x))
        = (fun x : ℝ => (∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * x)) * Real.cos (c * x)) := by
      funext x
      rw [Finset.prod_Icc_succ_top (by omega : 1 ≤ n + 1), hc]
    have hC : ∀ x : ℝ, HasDerivAt (fun y : ℝ => Real.cos (c * y)) (-Real.sin (c * x) * c) x := by
      intro x
      have h := ((hasDerivAt_id x).const_mul c).cos
      convert h using 1 <;> simp
    have hS : ∀ x : ℝ, HasDerivAt (fun y : ℝ => -Real.sin (c * y) * c)
        (-(Real.cos (c * x) * c) * c) x := by
      intro x
      have h := (((hasDerivAt_id x).const_mul c).sin.neg).mul_const c
      convert h using 1 <;> simp
    rw [hfun]
    refine ⟨fun x => D x * Real.cos (c * x)
        + (∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * x)) * (-Real.sin (c * x) * c),
      fun x => E x * Real.cos (c * x) + D x * (-Real.sin (c * x) * c)
        + (D x * (-Real.sin (c * x) * c)
          + (∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * x)) * (-(Real.cos (c * x) * c) * c)),
      fun x => (hP x).mul (hC x),
      fun x => ((hD x).mul (hC x)).add ((hP x).mul (hS x)), ?_, ?_, ?_⟩
    · simp [hD0]
    · dsimp only
      rw [hE0, hD0, hP0, Finset.sum_Icc_succ_top (by omega : 1 ≤ n + 1)]
      simp only [mul_zero, zero_mul, Real.cos_zero, Real.sin_zero, mul_one, one_mul,
        neg_zero, zero_add, add_zero]
      rw [hc]
      push_cast
      ring
    · simp

lemma putnam_deriv (n : ℕ) :
    iteratedDeriv 2 (fun x : ℝ => ∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * x)) 0
      = -∑ i ∈ Finset.Icc 1 n, (i : ℝ) ^ 2 := by
  obtain ⟨D, E, hP, hD, hD0, hE0, hP0⟩ := putnam_key n
  have h2 : iteratedDeriv 2 (fun x : ℝ => ∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * x))
      = deriv (deriv (fun x : ℝ => ∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * x))) := by
    rw [show (2 : ℕ) = 1 + 1 from rfl, iteratedDeriv_succ, iteratedDeriv_one]
  have hd1 : deriv (fun x : ℝ => ∏ i ∈ Finset.Icc 1 n, Real.cos ((i : ℝ) * x)) = D :=
    funext fun x => (hP x).deriv
  rw [h2, hd1, (hD 0).deriv, hE0]

lemma putnam_sum (n : ℕ) :
    6 * ∑ i ∈ Finset.Icc 1 n, (i : ℝ) ^ 2
      = (n : ℝ) * ((n : ℝ) + 1) * (2 * (n : ℝ) + 1) := by
  induction n with
  | zero =>
    have e : Finset.Icc 1 0 = (∅ : Finset ℕ) := by simp
    simp [e]
  | succ n ih =>
    rw [Finset.sum_Icc_succ_top (by omega : 1 ≤ n + 1), mul_add, ih]
    push_cast
    ring

theorem putnam_2023_a1 (f : ℕ → ℝ → ℝ)
  (hf : ∀ n > 0, f n = fun x : ℝ => ∏ i ∈ Finset.Icc 1 n, Real.cos (i * x)) :
  IsLeast {n | 0 < n ∧ |iteratedDeriv 2 (f n) 0| > 2023} putnam_2023_a1_solution := by
  show IsLeast {n : ℕ | 0 < n ∧ |iteratedDeriv 2 (f n) 0| > 2023} 18
  have key : ∀ n : ℕ, 0 < n → |iteratedDeriv 2 (f n) 0| = ∑ i ∈ Finset.Icc 1 n, (i : ℝ) ^ 2 := by
    intro n hn
    rw [hf n hn, putnam_deriv n, abs_neg,
      abs_of_nonneg (Finset.sum_nonneg fun i _ => sq_nonneg _)]
  refine ⟨?_, ?_⟩
  · show 0 < 18 ∧ |iteratedDeriv 2 (f 18) 0| > 2023
    refine ⟨by norm_num, ?_⟩
    rw [key 18 (by norm_num)]
    have h := putnam_sum 18
    push_cast at h
    linarith
  · intro n hn
    obtain ⟨hn0, hn⟩ := hn
    rw [key n hn0] at hn
    by_contra hlt
    push_neg at hlt
    have hle : n ≤ 17 := by omega
    have h := putnam_sum n
    have hn' : (n : ℝ) ≤ 17 := by exact_mod_cast hle
    have hn0' : (0 : ℝ) ≤ n := Nat.cast_nonneg n
    have hb : (n : ℝ) * ((n : ℝ) + 1) * (2 * (n : ℝ) + 1) ≤ 17 * 18 * 35 := by
      apply mul_le_mul (mul_le_mul (by linarith) (by linarith) (by linarith) (by norm_num))
        (by linarith) (by linarith) (by norm_num)
    linarith

#print axioms putnam_2023_a1
