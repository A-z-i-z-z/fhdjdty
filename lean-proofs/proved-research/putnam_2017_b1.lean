import Mathlib

open Finset

open Topology Filter

theorem putnam_aux_ext (x y : Fin 2 → ℝ) (h0 : x 0 = y 0) (h1 : x 1 = y 1) : x = y := by
  funext i
  fin_cases i
  · first | exact h0 | simpa using h0
  · first | exact h1 | simpa using h1

theorem putnam_aux_line (v w p : Fin 2 → ℝ) (hw : w ≠ 0) :
    p ∈ {q : Fin 2 → ℝ | ∃ t : ℝ, q = v + t • w} ↔
      (p 0 - v 0) * w 1 = (p 1 - v 1) * w 0 := by
  constructor
  · rintro ⟨t, rfl⟩
    simp only [Pi.add_apply, Pi.smul_apply, smul_eq_mul]
    ring
  · intro h
    by_cases h0 : w 0 = 0
    · have h1 : w 1 ≠ 0 := by
        intro h1
        apply hw
        exact putnam_aux_ext w (0 : Fin 2 → ℝ) (by simp [h0]) (by simp [h1])
      have hp0 : p 0 = v 0 := by
        rw [h0, mul_zero] at h
        rcases mul_eq_zero.mp h with h' | h'
        · linarith
        · exact absurd h' h1
      have hdiv : (p 1 - v 1) / w 1 * w 1 = p 1 - v 1 := by
        field_simp
      refine ⟨(p 1 - v 1) / w 1, ?_⟩
      apply putnam_aux_ext
      · simp only [Pi.add_apply, Pi.smul_apply, smul_eq_mul]
        rw [h0, mul_zero, add_zero]
        exact hp0
      · simp only [Pi.add_apply, Pi.smul_apply, smul_eq_mul]
        rw [hdiv]
        ring
    · have hdiv : (p 0 - v 0) / w 0 * w 0 = p 0 - v 0 := by
        field_simp
      have key : (p 0 - v 0) / w 0 * w 1 = p 1 - v 1 := by
        rw [div_mul_eq_mul_div, div_eq_iff h0]
        first | exact h | linarith
      refine ⟨(p 0 - v 0) / w 0, ?_⟩
      apply putnam_aux_ext
      · simp only [Pi.add_apply, Pi.smul_apply, smul_eq_mul]
        rw [hdiv]
        ring
      · simp only [Pi.add_apply, Pi.smul_apply, smul_eq_mul]
        rw [key]
        ring

theorem putnam_aux_scal (w : Fin 2 → ℝ) (hw : w ≠ 0) (c : ℝ)
    (h0 : c * w 0 = 0) (h1 : c * w 1 = 0) : c = 0 := by
  by_contra hc
  apply hw
  apply putnam_aux_ext
  · have hw0 : w 0 = 0 := (mul_eq_zero.mp h0).resolve_left hc
    show w 0 = 0
    exact hw0
  · have hw1 : w 1 = 0 := (mul_eq_zero.mp h1).resolve_left hc
    show w 1 = 0
    exact hw1

theorem putnam_aux_nonparallel (v1 w1 v2 w2 O : Fin 2 → ℝ) (hw1 : w1 ≠ 0) (hw2 : w2 ≠ 0)
    (hO1 : (O 0 - v1 0) * w1 1 = (O 1 - v1 1) * w1 0)
    (hO2 : (O 0 - v2 0) * w2 1 = (O 1 - v2 1) * w2 0)
    (hne : {p : Fin 2 → ℝ | ∃ t : ℝ, p = v1 + t • w1} ≠ {p : Fin 2 → ℝ | ∃ t : ℝ, p = v2 + t • w2}) :
    w1 0 * w2 1 - w1 1 * w2 0 ≠ 0 := by
  intro hD
  apply hne
  ext p
  rw [putnam_aux_line v1 w1 p hw1, putnam_aux_line v2 w2 p hw2]
  have e1 : (p 0 - v1 0) * w1 1 - (p 1 - v1 1) * w1 0
      = (p 0 - O 0) * w1 1 - (p 1 - O 1) * w1 0 := by
    linear_combination hO1
  have e2 : (p 0 - v2 0) * w2 1 - (p 1 - v2 1) * w2 0
      = (p 0 - O 0) * w2 1 - (p 1 - O 1) * w2 0 := by
    linear_combination hO2
  have f0 : ((p 0 - O 0) * w2 1 - (p 1 - O 1) * w2 0) * w1 0
      = ((p 0 - O 0) * w1 1 - (p 1 - O 1) * w1 0) * w2 0 := by
    linear_combination (p 0 - O 0) * hD
  have f1 : ((p 0 - O 0) * w2 1 - (p 1 - O 1) * w2 0) * w1 1
      = ((p 0 - O 0) * w1 1 - (p 1 - O 1) * w1 0) * w2 1 := by
    linear_combination (p 1 - O 1) * hD
  constructor
  · intro h
    have hc1 : (p 0 - O 0) * w1 1 - (p 1 - O 1) * w1 0 = 0 := by
      linear_combination h - e1
    have hc2 : (p 0 - O 0) * w2 1 - (p 1 - O 1) * w2 0 = 0 := by
      apply putnam_aux_scal w1 hw1
      · linear_combination f0 + w2 0 * hc1
      · linear_combination f1 + w2 1 * hc1
    linear_combination e2 + hc2
  · intro h
    have hc2 : (p 0 - O 0) * w2 1 - (p 1 - O 1) * w2 0 = 0 := by
      linear_combination h - e2
    have hc1 : (p 0 - O 0) * w1 1 - (p 1 - O 1) * w1 0 = 0 := by
      apply putnam_aux_scal w2 hw2
      · linear_combination (-1 : ℝ) * f0 + w1 0 * hc2
      · linear_combination (-1 : ℝ) * f1 + w1 1 * hc2
    linear_combination e1 + hc1

theorem putnam_aux_mkpt (x y : ℝ) : ∃ P : Fin 2 → ℝ, P 0 = x ∧ P 1 = y :=
  ⟨![x, y], by simp, by simp⟩

theorem putnam_aux_exists_point (v1 w1 v2 w2 : Fin 2 → ℝ) (hw1 : w1 ≠ 0) (hw2 : w2 ≠ 0)
    (ha : (v1 0 - v2 0) * w2 1 - (v1 1 - v2 1) * w2 0 ≠ 0) :
    ∃ P : Fin 2 → ℝ, P ∉ {p : Fin 2 → ℝ | ∃ t : ℝ, p = v1 + t • w1} ∧
      P ∉ {p : Fin 2 → ℝ | ∃ t : ℝ, p = v2 + t • w2} := by
  have hN : w1 1 * w1 1 + w1 0 * w1 0 ≠ 0 := by
    intro hN
    apply hw1
    have h0 : w1 0 * w1 0 = 0 := by
      linarith [mul_self_nonneg (w1 1), mul_self_nonneg (w1 0)]
    have h1 : w1 1 * w1 1 = 0 := by
      linarith [mul_self_nonneg (w1 1), mul_self_nonneg (w1 0)]
    apply putnam_aux_ext
    · show w1 0 = 0
      exact mul_self_eq_zero.mp h0
    · show w1 1 = 0
      exact mul_self_eq_zero.mp h1
  obtain ⟨c, hc0, hc2⟩ : ∃ c : ℝ, c ≠ 0 ∧
      (v1 0 - v2 0) * w2 1 - (v1 1 - v2 1) * w2 0
        + c * (w1 1 * w2 1 + w1 0 * w2 0) ≠ 0 := by
    by_cases hb : (v1 0 - v2 0) * w2 1 - (v1 1 - v2 1) * w2 0
        + 1 * (w1 1 * w2 1 + w1 0 * w2 0) = 0
    · refine ⟨2, by norm_num, ?_⟩
      intro h2
      apply ha
      linarith
    · exact ⟨1, one_ne_zero, hb⟩
  obtain ⟨P, hP0, hP1⟩ := putnam_aux_mkpt (v1 0 + c * w1 1) (v1 1 - c * w1 0)
  refine ⟨P, ?_, ?_⟩
  · rw [putnam_aux_line v1 w1 P hw1, hP0, hP1]
    intro h
    have hcN : c * (w1 1 * w1 1 + w1 0 * w1 0) = 0 := by
      linear_combination h
    exact hc0 ((mul_eq_zero.mp hcN).resolve_right hN)
  · rw [putnam_aux_line v2 w2 P hw2, hP0, hP1]
    intro h
    exact hc2 (by linear_combination h)

theorem putnam_2017_b1 (lines : Set (Set (Fin 2 → ℝ)))
  (L1 L2 : Set (Fin 2 → ℝ))
  (L1L2lines : L1 ∈ lines ∧ L2 ∈ lines)
  (L1L2distinct : L1 ≠ L2)
  (hlines : lines = {L | ∃ v w : Fin 2 → ℝ, w ≠ 0 ∧ L = {p | ∃ t : ℝ, p = v + t • w}}) :
  L1 ∩ L2 ≠ ∅ ↔ (∀ lambda : ℝ, lambda ≠ 0 →
    ∀ P, (P ∉ L1 ∧ P ∉ L2) → ∃ A1 A2, A1 ∈ L1 ∧ A2 ∈ L2 ∧ (A2 - P = lambda • (A1 - P))) := by
  subst hlines
  obtain ⟨v1, w1, hw1, hE1⟩ := L1L2lines.1
  obtain ⟨v2, w2, hw2, hE2⟩ := L1L2lines.2
  subst hE1
  subst hE2
  constructor
  · intro hne
    obtain ⟨O, hO1, hO2⟩ := Set.nonempty_iff_ne_empty.mpr hne
    have hO1' := (putnam_aux_line v1 w1 O hw1).mp hO1
    have hO2' := (putnam_aux_line v2 w2 O hw2).mp hO2
    have hD := putnam_aux_nonparallel v1 w1 v2 w2 O hw1 hw2 hO1' hO2' L1L2distinct
    intro lam hlam P _hP
    have hne' : lam * (w1 0 * w2 1 - w1 1 * w2 0) ≠ 0 := mul_ne_zero hlam hD
    obtain ⟨s, hs⟩ : ∃ s : ℝ, lam * (w1 0 * w2 1 - w1 1 * w2 0) * s
        = -(1 - lam) * ((P 0 - O 0) * w2 1 - (P 1 - O 1) * w2 0) :=
      ⟨-(1 - lam) * ((P 0 - O 0) * w2 1 - (P 1 - O 1) * w2 0)
          / (lam * (w1 0 * w2 1 - w1 1 * w2 0)),
        by first
          | field_simp
          | rw [mul_div_assoc', mul_div_cancel_left₀ _ hne']⟩
    refine ⟨O + s • w1, P + lam • ((O + s • w1) - P), ?_, ?_, ?_⟩
    · apply (putnam_aux_line v1 w1 _ hw1).mpr
      simp only [Pi.add_apply, Pi.sub_apply, Pi.smul_apply, smul_eq_mul]
      linear_combination hO1'
    · apply (putnam_aux_line v2 w2 _ hw2).mpr
      simp only [Pi.add_apply, Pi.sub_apply, Pi.smul_apply, smul_eq_mul]
      linear_combination hs + hO2'
    · apply putnam_aux_ext
      · simp only [Pi.add_apply, Pi.sub_apply, Pi.smul_apply, smul_eq_mul]
        ring
      · simp only [Pi.add_apply, Pi.sub_apply, Pi.smul_apply, smul_eq_mul]
        ring
  · intro h hI
    have ha : (v1 0 - v2 0) * w2 1 - (v1 1 - v2 1) * w2 0 ≠ 0 := by
      intro ha0
      have hv : v1 ∈ ({p : Fin 2 → ℝ | ∃ t : ℝ, p = v1 + t • w1}
          ∩ {p : Fin 2 → ℝ | ∃ t : ℝ, p = v2 + t • w2}) := by
        refine ⟨⟨0, by simp⟩, ?_⟩
        rw [putnam_aux_line v2 w2 v1 hw2]
        linarith
      rw [hI] at hv
      simp at hv
    obtain ⟨P, hP1, hP2⟩ := putnam_aux_exists_point v1 w1 v2 w2 hw1 hw2 ha
    obtain ⟨A1, A2, hA1, hA2, hEq⟩ := h 1 one_ne_zero P ⟨hP1, hP2⟩
    have hA : A2 = A1 := by
      rw [one_smul] at hEq
      exact sub_left_inj.mp hEq
    have hmem : A1 ∈ ({p : Fin 2 → ℝ | ∃ t : ℝ, p = v1 + t • w1}
        ∩ {p : Fin 2 → ℝ | ∃ t : ℝ, p = v2 + t • w2}) :=
      ⟨hA1, by rw [← hA]; exact hA2⟩
    rw [hI] at hmem
    simp at hmem

#print axioms putnam_2017_b1
