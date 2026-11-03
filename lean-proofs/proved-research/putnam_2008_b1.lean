import Mathlib

open Finset

open Filter Topology Set
abbrev putnam_2008_b1_solution : ℕ := 2
def is_rational_point (p : Fin 2 → ℝ) : Prop := ∃ (a b : ℚ), a = p 0 ∧ b = p 1
def real_circle (c : EuclideanSpace ℝ (Fin 2)) (r : ℝ) : Set (EuclideanSpace ℝ (Fin 2)) := {p | dist p c = r}

lemma pb1_sumsq (s t : ℝ) (h : ¬ (s = 0 ∧ t = 0)) : s ^ 2 + t ^ 2 ≠ 0 := by
  intro h0
  apply h
  have hs : s ^ 2 = 0 := by nlinarith [sq_nonneg s, sq_nonneg t]
  have ht : t ^ 2 = 0 := by nlinarith [sq_nonneg s, sq_nonneg t]
  exact ⟨(pow_eq_zero_iff two_ne_zero).mp hs, (pow_eq_zero_iff two_ne_zero).mp ht⟩

lemma pb1_pair_ne (a b c d : ℚ) (h : (a, b) ≠ (c, d)) :
    ¬ ((a : ℝ) - (c : ℝ) = 0 ∧ (b : ℝ) - (d : ℝ) = 0) := by
  rintro ⟨h1, h2⟩
  apply h
  have e1 : a = c := by exact_mod_cast (show (a : ℝ) = (c : ℝ) by linarith)
  have e2 : b = d := by exact_mod_cast (show (b : ℝ) = (d : ℝ) by linarith)
  rw [e1, e2]

lemma pb1_alg (u1 u2 v1 v2 w1 w2 : ℝ) (hD : u1 * v2 - u2 * v1 = 0)
    (hA : 2 * (w1 * u1 + w2 * u2) + (u1 ^ 2 + u2 ^ 2) = 0)
    (hB : 2 * (w1 * v1 + w2 * v2) + (v1 ^ 2 + v2 ^ 2) = 0)
    (hu : u1 ^ 2 + u2 ^ 2 ≠ 0) (hv : v1 ^ 2 + v2 ^ 2 ≠ 0)
    (huv : (u1 - v1) ^ 2 + (u2 - v2) ^ 2 ≠ 0) : False := by
  have P1 : (u1 ^ 2 + u2 ^ 2) * v1 = (u1 * v1 + u2 * v2) * u1 := by
    linear_combination (-u2) * hD
  have P2 : (u1 ^ 2 + u2 ^ 2) * v2 = (u1 * v1 + u2 * v2) * u2 := by
    linear_combination u1 * hD
  have P3 : (v1 ^ 2 + v2 ^ 2) * u1 = (u1 * v1 + u2 * v2) * v1 := by
    linear_combination v2 * hD
  have P4 : (v1 ^ 2 + v2 ^ 2) * u2 = (u1 * v1 + u2 * v2) * v2 := by
    linear_combination (-v1) * hD
  have K1 : (u1 ^ 2 + u2 ^ 2) * ((v1 ^ 2 + v2 ^ 2) - (u1 * v1 + u2 * v2)) = 0 := by
    linear_combination (u1 ^ 2 + u2 ^ 2) * hB - (u1 * v1 + u2 * v2) * hA
      - 2 * w1 * P1 - 2 * w2 * P2
  have K2 : (v1 ^ 2 + v2 ^ 2) * ((u1 ^ 2 + u2 ^ 2) - (u1 * v1 + u2 * v2)) = 0 := by
    linear_combination (v1 ^ 2 + v2 ^ 2) * hA - (u1 * v1 + u2 * v2) * hB
      - 2 * w1 * P3 - 2 * w2 * P4
  have E1 : (u1 ^ 2 + u2 ^ 2) - (u1 * v1 + u2 * v2) = 0 := by
    rcases mul_eq_zero.mp K2 with h | h
    · exact absurd h hv
    · exact h
  have E2 : (v1 ^ 2 + v2 ^ 2) - (u1 * v1 + u2 * v2) = 0 := by
    rcases mul_eq_zero.mp K1 with h | h
    · exact absurd h hu
    · exact h
  apply huv
  linear_combination E1 + E2

lemma pb1_center (a1 b1 a2 b2 a3 b3 : ℚ) (X Y R : ℝ)
    (E1 : ((a1 : ℝ) - X) ^ 2 + ((b1 : ℝ) - Y) ^ 2 = R)
    (E2 : ((a2 : ℝ) - X) ^ 2 + ((b2 : ℝ) - Y) ^ 2 = R)
    (E3 : ((a3 : ℝ) - X) ^ 2 + ((b3 : ℝ) - Y) ^ 2 = R)
    (d12 : (a1, b1) ≠ (a2, b2)) (d13 : (a1, b1) ≠ (a3, b3)) (d23 : (a2, b2) ≠ (a3, b3)) :
    ∃ p q : ℚ, (p : ℝ) = X ∧ (q : ℝ) = Y := by
  have hu := pb1_sumsq _ _ (pb1_pair_ne a2 b2 a1 b1 d12.symm)
  have hv := pb1_sumsq _ _ (pb1_pair_ne a3 b3 a1 b1 d13.symm)
  have huv := pb1_sumsq (((a2 : ℝ) - a1) - ((a3 : ℝ) - a1)) (((b2 : ℝ) - b1) - ((b3 : ℝ) - b1))
    (fun h => pb1_pair_ne a2 b2 a3 b3 d23 ⟨by linarith [h.1], by linarith [h.2]⟩)
  by_cases hD : (a2 - a1) * (b3 - b1) - (a3 - a1) * (b2 - b1) = 0
  · exfalso
    have hDR : ((a2 : ℝ) - a1) * ((b3 : ℝ) - b1) - ((b2 : ℝ) - b1) * ((a3 : ℝ) - a1) = 0 := by
      have h' : (((a2 - a1) * (b3 - b1) - (a3 - a1) * (b2 - b1) : ℚ) : ℝ) = 0 := by
        rw [hD, Rat.cast_zero]
      push_cast at h'
      linear_combination h'
    exact pb1_alg ((a2 : ℝ) - a1) ((b2 : ℝ) - b1) ((a3 : ℝ) - a1) ((b3 : ℝ) - b1)
      ((a1 : ℝ) - X) ((b1 : ℝ) - Y) hDR
      (by linear_combination E2 - E1) (by linear_combination E3 - E1) hu hv huv
  · have hM : ((2 * ((a2 - a1) * (b3 - b1) - (a3 - a1) * (b2 - b1)) : ℚ) : ℝ) ≠ 0 := by
      exact_mod_cast mul_ne_zero two_ne_zero hD
    have L2 : 2 * ((a2 : ℝ) - a1) * X + 2 * ((b2 : ℝ) - b1) * Y
        = (a2 : ℝ) ^ 2 + (b2 : ℝ) ^ 2 - ((a1 : ℝ) ^ 2 + (b1 : ℝ) ^ 2) := by
      linear_combination E1 - E2
    have L3 : 2 * ((a3 : ℝ) - a1) * X + 2 * ((b3 : ℝ) - b1) * Y
        = (a3 : ℝ) ^ 2 + (b3 : ℝ) ^ 2 - ((a1 : ℝ) ^ 2 + (b1 : ℝ) ^ 2) := by
      linear_combination E1 - E3
    refine ⟨((a2 ^ 2 + b2 ^ 2 - (a1 ^ 2 + b1 ^ 2)) * (b3 - b1)
        - (b2 - b1) * (a3 ^ 2 + b3 ^ 2 - (a1 ^ 2 + b1 ^ 2)))
        / (2 * ((a2 - a1) * (b3 - b1) - (a3 - a1) * (b2 - b1))),
      ((a2 - a1) * (a3 ^ 2 + b3 ^ 2 - (a1 ^ 2 + b1 ^ 2))
        - (a3 - a1) * (a2 ^ 2 + b2 ^ 2 - (a1 ^ 2 + b1 ^ 2)))
        / (2 * ((a2 - a1) * (b3 - b1) - (a3 - a1) * (b2 - b1))), ?_, ?_⟩
    · rw [Rat.cast_div, div_eq_iff hM]
      push_cast
      linear_combination (-((b3 : ℝ) - b1)) * L2 + ((b2 : ℝ) - b1) * L3
    · rw [Rat.cast_div, div_eq_iff hM]
      push_cast
      linear_combination ((a3 : ℝ) - a1) * L2 - ((a2 : ℝ) - a1) * L3

lemma pb1_eq (p q : EuclideanSpace ℝ (Fin 2)) (h0 : p 0 = q 0) (h1 : p 1 = q 1) : p = q := by
  ext i
  fin_cases i
  · exact h0
  · exact h1

lemma pb1_dist_sq (p c : EuclideanSpace ℝ (Fin 2)) :
    dist p c ^ 2 = (p 0 - c 0) ^ 2 + (p 1 - c 1) ^ 2 := by
  rw [EuclideanSpace.dist_eq, Real.sq_sqrt]
  · simp [Fin.sum_univ_two, Real.dist_eq, sq_abs]
  · positivity

lemma pb1_pt (c : EuclideanSpace ℝ (Fin 2)) (r : ℝ) (x : EuclideanSpace ℝ (Fin 2))
    (hx : x ∈ {p : EuclideanSpace ℝ (Fin 2) | p ∈ real_circle c r ∧ is_rational_point p}) :
    ∃ a b : ℚ, (a : ℝ) = x 0 ∧ (b : ℝ) = x 1 ∧ ((a : ℝ) - c 0) ^ 2 + ((b : ℝ) - c 1) ^ 2 = r ^ 2 := by
  obtain ⟨hxc, a, b, ha, hb⟩ := hx
  refine ⟨a, b, ha, hb, ?_⟩
  have hd : dist x c = r := hxc
  have hsq := pb1_dist_sq x c
  rw [hd, ← ha, ← hb] at hsq
  exact hsq.symm

lemma pb1_sqrt2 : Real.sqrt 2 ^ 2 = 2 := Real.sq_sqrt (by norm_num)

lemma pb1_C0 : (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2)) 0 = Real.sqrt 2 := by simp
lemma pb1_C1 : (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2)) 1 = 0 := by simp
lemma pb1_P0 : (!₂[(0:ℝ), 1] : EuclideanSpace ℝ (Fin 2)) 0 = 0 := by simp
lemma pb1_P1 : (!₂[(0:ℝ), 1] : EuclideanSpace ℝ (Fin 2)) 1 = 1 := by simp
lemma pb1_Q0 : (!₂[(0:ℝ), -1] : EuclideanSpace ℝ (Fin 2)) 0 = 0 := by simp
lemma pb1_Q1 : (!₂[(0:ℝ), -1] : EuclideanSpace ℝ (Fin 2)) 1 = -1 := by simp

theorem putnam_2008_b1 :
  IsGreatest {n : ℕ | ∃ (c : EuclideanSpace ℝ (Fin 2)) (r : ℝ), ¬ is_rational_point c ∧ (Set.ncard {p : EuclideanSpace ℝ (Fin 2) | p ∈ real_circle c r ∧ is_rational_point p} = n)} putnam_2008_b1_solution := by
  unfold IsGreatest
  constructor
  · rw [Set.mem_setOf_eq]
    refine ⟨!₂[Real.sqrt 2, (0:ℝ)], Real.sqrt 3, ?_, ?_⟩
    · rintro ⟨a, b, ha, _⟩
      have h0 : (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2)) 0 = Real.sqrt 2 := pb1_C0
      exact irrational_sqrt_two (Set.mem_range.mpr ⟨a, ha.trans h0⟩)
    · have hne : (!₂[(0:ℝ), 1] : EuclideanSpace ℝ (Fin 2)) ≠ !₂[(0:ℝ), -1] := by
        intro h
        have h1 := congrArg (fun x : EuclideanSpace ℝ (Fin 2) => x 1) h
        simp only [pb1_P1, pb1_Q1] at h1
        norm_num at h1
      convert Set.ncard_pair hne using 2
      ext p
      simp only [Set.mem_setOf_eq, Set.mem_insert_iff, Set.mem_singleton_iff]
      constructor
      · rintro ⟨hpc, a, b, ha, hb⟩
        have hd : dist p (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2)) = Real.sqrt 3 := hpc
        have hsq := pb1_dist_sq p (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2))
        rw [hd, Real.sq_sqrt (show (0:ℝ) ≤ 3 by norm_num), pb1_C0, pb1_C1, ← ha, ← hb] at hsq
        by_cases ha0 : a = 0
        · have hb2 : (b : ℝ) ^ 2 = 1 := by
            have ha0r : (a : ℝ) = 0 := by rw [ha0, Rat.cast_zero]
            rw [ha0r] at hsq
            linear_combination -hsq - pb1_sqrt2
          have hbq : b ^ 2 = 1 := by exact_mod_cast hb2
          have hfac : (b - 1) * (b + 1) = 0 := by linear_combination hbq
          rcases mul_eq_zero.mp hfac with h | h
          · left
            apply pb1_eq
            · simp [← ha, ha0, pb1_P0]
            · have hb1 : b = 1 := by linarith
              simp [← hb, hb1, pb1_P1]
          · right
            apply pb1_eq
            · simp [← ha, ha0, pb1_Q0]
            · have hb1 : b = -1 := by linarith
              simp [← hb, hb1, pb1_Q1]
        · exfalso
          have hne' : (a : ℝ) ≠ 0 := by exact_mod_cast ha0
          have key : (a : ℝ) ^ 2 + (b : ℝ) ^ 2 - 1 = 2 * Real.sqrt 2 * (a : ℝ) := by
            linear_combination -hsq - pb1_sqrt2
          refine irrational_sqrt_two (Set.mem_range.mpr ⟨(a ^ 2 + b ^ 2 - 1) / (2 * a), ?_⟩)
          push_cast
          rw [div_eq_iff (mul_ne_zero two_ne_zero hne')]
          linear_combination key
      · rintro (rfl | rfl)
        · have hsqP : dist (!₂[(0:ℝ), 1] : EuclideanSpace ℝ (Fin 2))
              (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2)) ^ 2 = 3 := by
            rw [pb1_dist_sq, pb1_C0, pb1_C1, pb1_P0, pb1_P1]
            linear_combination pb1_sqrt2
          have hdP : dist (!₂[(0:ℝ), 1] : EuclideanSpace ℝ (Fin 2))
              (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2)) = Real.sqrt 3 := by
            rw [← hsqP, Real.sqrt_sq dist_nonneg]
          refine ⟨hdP, ?_⟩
          unfold is_rational_point
          exact ⟨0, 1, by simp [pb1_P0], by simp [pb1_P1]⟩
        · have hsqQ : dist (!₂[(0:ℝ), -1] : EuclideanSpace ℝ (Fin 2))
              (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2)) ^ 2 = 3 := by
            rw [pb1_dist_sq, pb1_C0, pb1_C1, pb1_Q0, pb1_Q1]
            linear_combination pb1_sqrt2
          have hdQ : dist (!₂[(0:ℝ), -1] : EuclideanSpace ℝ (Fin 2))
              (!₂[Real.sqrt 2, (0:ℝ)] : EuclideanSpace ℝ (Fin 2)) = Real.sqrt 3 := by
            rw [← hsqQ, Real.sqrt_sq dist_nonneg]
          refine ⟨hdQ, ?_⟩
          unfold is_rational_point
          exact ⟨0, -1, by simp [pb1_Q0], by simp [pb1_Q1]⟩
  · intro n hn
    obtain ⟨c, r, hc, rfl⟩ := hn
    show Set.ncard _ ≤ 2
    by_contra hlt
    push_neg at hlt
    have hfin : {p : EuclideanSpace ℝ (Fin 2) | p ∈ real_circle c r ∧ is_rational_point p}.Finite := by
      by_contra hinf
      rw [Set.Infinite.ncard hinf] at hlt
      omega
    obtain ⟨p, q, s, hp, hq, hs, hpq, hps, hqs⟩ := (Set.two_lt_ncard_iff hfin).mp hlt
    obtain ⟨a1, b1, ha1, hb1, E1⟩ := pb1_pt c r p hp
    obtain ⟨a2, b2, ha2, hb2, E2⟩ := pb1_pt c r q hq
    obtain ⟨a3, b3, ha3, hb3, E3⟩ := pb1_pt c r s hs
    have d12 : (a1, b1) ≠ (a2, b2) := by
      intro h
      simp only [Prod.mk.injEq] at h
      exact hpq (pb1_eq p q (by rw [← ha1, ← ha2, h.1]) (by rw [← hb1, ← hb2, h.2]))
    have d13 : (a1, b1) ≠ (a3, b3) := by
      intro h
      simp only [Prod.mk.injEq] at h
      exact hps (pb1_eq p s (by rw [← ha1, ← ha3, h.1]) (by rw [← hb1, ← hb3, h.2]))
    have d23 : (a2, b2) ≠ (a3, b3) := by
      intro h
      simp only [Prod.mk.injEq] at h
      exact hqs (pb1_eq q s (by rw [← ha2, ← ha3, h.1]) (by rw [← hb2, ← hb3, h.2]))
    obtain ⟨px, py, hpx, hpy⟩ :=
      pb1_center a1 b1 a2 b2 a3 b3 (c 0) (c 1) (r ^ 2) E1 E2 E3 d12 d13 d23
    apply hc
    unfold is_rational_point
    exact ⟨px, py, hpx, hpy⟩

#print axioms putnam_2008_b1
