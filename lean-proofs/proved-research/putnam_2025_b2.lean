import Mathlib

open Finset

open Set Real MeasureTheory Interval

theorem ii_aux_b2 {u : ℝ → ℝ} (hu : ContinuousOn u (Icc (0:ℝ) 1)) :
    IntervalIntegrable u MeasureTheory.volume 0 1 := by
  apply ContinuousOn.intervalIntegrable
  rw [Set.uIcc_of_le zero_le_one]
  exact hu

theorem putnam_2025_b2 (f : ℝ → ℝ)
    (hf_cont : ContinuousOn f (Icc 0 1))
    (hf_mono : StrictMonoOn f (Icc 0 1))
    (hf_nonneg : ∀ x ∈ Icc (0 : ℝ) 1, 0 ≤ f x) :
    (∫ x in (0:ℝ)..1, x * f x) / (∫ x in (0:ℝ)..1, f x) <
    (∫ x in (0:ℝ)..1, x * (f x) ^ 2) / (∫ x in (0:ℝ)..1, (f x) ^ 2) := by
  have hmono : MonotoneOn f (Icc (0:ℝ) 1) := hf_mono.monotoneOn
  have hfpos : ∀ x ∈ Ioo (0:ℝ) 1, 0 < f x := by
    intro x hx
    have h := hf_mono (show (0:ℝ) ∈ Icc (0:ℝ) 1 from ⟨le_refl 0, zero_le_one⟩)
      (show x ∈ Icc (0:ℝ) 1 from ⟨hx.1.le, hx.2.le⟩) hx.1
    linarith [hf_nonneg 0 ⟨le_refl 0, zero_le_one⟩]
  have c1 : ContinuousOn (fun x : ℝ => x * f x) (Icc (0:ℝ) 1) :=
    continuousOn_id.mul hf_cont
  have c2 : ContinuousOn (fun x : ℝ => f x ^ 2) (Icc (0:ℝ) 1) := hf_cont.pow 2
  have c3 : ContinuousOn (fun x : ℝ => x * f x ^ 2) (Icc (0:ℝ) 1) :=
    continuousOn_id.mul c2
  have c5 : ContinuousOn (fun x : ℝ => (1 - x) * f x) (Icc (0:ℝ) 1) :=
    (continuousOn_const.sub continuousOn_id).mul hf_cont
  obtain ⟨m, hm⟩ : ∃ m : ℝ,
      m = (∫ x in (0:ℝ)..1, x * f x) / ∫ x in (0:ℝ)..1, f x := ⟨_, rfl⟩
  have c4 : ContinuousOn (fun x : ℝ => (x - m) * f x * (f x - f m)) (Icc (0:ℝ) 1) :=
    ((continuousOn_id.sub continuousOn_const).mul hf_cont).mul
      (hf_cont.sub continuousOn_const)
  have hF : 0 < ∫ x in (0:ℝ)..1, f x :=
    intervalIntegral.intervalIntegral_pos_of_pos_on (ii_aux_b2 hf_cont) hfpos zero_lt_one
  have hFne : (∫ x in (0:ℝ)..1, f x) ≠ 0 := hF.ne'
  have hpow2pos : ∀ x ∈ Ioo (0:ℝ) 1, 0 < f x ^ 2 := fun x hx => pow_pos (hfpos x hx) 2
  have hH : 0 < ∫ x in (0:ℝ)..1, f x ^ 2 :=
    intervalIntegral.intervalIntegral_pos_of_pos_on (ii_aux_b2 c2) hpow2pos zero_lt_one
  have hG0 : 0 ≤ ∫ x in (0:ℝ)..1, x * f x :=
    intervalIntegral.integral_nonneg zero_le_one (fun x hx => mul_nonneg hx.1 (hf_nonneg x hx))
  have hp1pos : ∀ x ∈ Ioo (0:ℝ) 1, 0 < (1 - x) * f x :=
    fun x hx => mul_pos (by linarith [hx.2]) (hfpos x hx)
  have hp1 : 0 < ∫ x in (0:ℝ)..1, (1 - x) * f x :=
    intervalIntegral.intervalIntegral_pos_of_pos_on (ii_aux_b2 c5) hp1pos zero_lt_one
  have hsubA : ∫ x in (0:ℝ)..1, (1 - x) * f x
      = (∫ x in (0:ℝ)..1, f x) - ∫ x in (0:ℝ)..1, x * f x := by
    rw [← intervalIntegral.integral_sub (ii_aux_b2 hf_cont) (ii_aux_b2 c1)]
    congr 1
    funext x
    ring
  have hm0 : 0 ≤ m := by
    rw [hm]
    exact div_nonneg hG0 hF.le
  have hm1 : m < 1 := by
    rw [hm, div_lt_one hF]
    linarith
  have hmI : m ∈ Icc (0:ℝ) 1 := ⟨hm0, hm1.le⟩
  have hG : (∫ x in (0:ℝ)..1, x * f x) = m * ∫ x in (0:ℝ)..1, f x := by
    rw [hm]
    field_simp
  have hnn : ∀ x ∈ Icc (0:ℝ) 1, 0 ≤ (x - m) * f x * (f x - f m) := by
    intro x hx
    rcases le_total x m with h | h
    · have hfm : f x ≤ f m := hmono hx hmI h
      nlinarith [mul_nonneg (mul_nonneg (sub_nonneg.2 h) (sub_nonneg.2 hfm)) (hf_nonneg x hx)]
    · have hfm : f m ≤ f x := hmono hmI hx h
      nlinarith [mul_nonneg (mul_nonneg (sub_nonneg.2 h) (sub_nonneg.2 hfm)) (hf_nonneg x hx)]
  have hpos2 : ∀ x ∈ Ioo m 1, 0 < (x - m) * f x * (f x - f m) := by
    intro x hx
    have hx0 : (0:ℝ) < x := lt_of_le_of_lt hm0 hx.1
    have hxI : x ∈ Icc (0:ℝ) 1 := ⟨hx0.le, hx.2.le⟩
    have hfm : f m < f x := hf_mono hmI hxI hx.1
    have hfx : 0 < f x := hfpos x ⟨hx0, hx.2⟩
    have hxm : 0 < x - m := sub_pos.2 hx.1
    have hfm' : 0 < f x - f m := sub_pos.2 hfm
    exact mul_pos (mul_pos hxm hfx) hfm'
  have hsub1 : Set.uIcc (0:ℝ) m ⊆ Icc (0:ℝ) 1 := by
    rw [Set.uIcc_of_le hm0]
    exact Icc_subset_Icc le_rfl hm1.le
  have hsub2 : Set.uIcc m (1:ℝ) ⊆ Icc (0:ℝ) 1 := by
    rw [Set.uIcc_of_le hm1.le]
    exact Icc_subset_Icc hm0 le_rfl
  have hi1 : IntervalIntegrable (fun x : ℝ => (x - m) * f x * (f x - f m))
      MeasureTheory.volume 0 m :=
    ContinuousOn.intervalIntegrable (c4.mono hsub1)
  have hi2 : IntervalIntegrable (fun x : ℝ => (x - m) * f x * (f x - f m))
      MeasureTheory.volume m 1 :=
    ContinuousOn.intervalIntegrable (c4.mono hsub2)
  have hsplit : ∫ x in (0:ℝ)..1, (x - m) * f x * (f x - f m)
      = (∫ x in (0:ℝ)..m, (x - m) * f x * (f x - f m))
        + ∫ x in m..1, (x - m) * f x * (f x - f m) :=
    (intervalIntegral.integral_add_adjacent_intervals hi1 hi2).symm
  have hL : 0 ≤ ∫ x in (0:ℝ)..m, (x - m) * f x * (f x - f m) :=
    intervalIntegral.integral_nonneg hm0 (fun x hx => hnn x ⟨hx.1, hx.2.trans hm1.le⟩)
  have hR : 0 < ∫ x in m..1, (x - m) * f x * (f x - f m) :=
    intervalIntegral.intervalIntegral_pos_of_pos_on hi2 hpos2 hm1
  have hP : 0 < ∫ x in (0:ℝ)..1, (x - m) * f x * (f x - f m) := by
    rw [hsplit]
    linarith
  have hA : IntervalIntegrable (fun x : ℝ => x * f x ^ 2) MeasureTheory.volume 0 1 :=
    ii_aux_b2 c3
  have hB : IntervalIntegrable (fun x : ℝ => f x ^ 2) MeasureTheory.volume 0 1 :=
    ii_aux_b2 c2
  have hC : IntervalIntegrable (fun x : ℝ => x * f x) MeasureTheory.volume 0 1 :=
    ii_aux_b2 c1
  have hD : IntervalIntegrable (fun x : ℝ => f x) MeasureTheory.volume 0 1 :=
    ii_aux_b2 hf_cont
  have hmB := hB.const_mul m
  have hmD := hD.const_mul m
  have hkey : ∫ x in (0:ℝ)..1, (x - m) * f x * (f x - f m)
      = (∫ x in (0:ℝ)..1, x * f x ^ 2) - m * (∫ x in (0:ℝ)..1, f x ^ 2)
        - f m * ((∫ x in (0:ℝ)..1, x * f x) - m * (∫ x in (0:ℝ)..1, f x)) := by
    have hT : ∫ x in (0:ℝ)..1, (x - m) * f x * (f x - f m)
        = ∫ x in (0:ℝ)..1, (x * f x ^ 2 - m * f x ^ 2 - f m * (x * f x - m * f x)) := by
      congr 1
      funext x
      ring
    rw [hT, intervalIntegral.integral_sub (hA.sub hmB) ((hC.sub hmD).const_mul (f m)),
      intervalIntegral.integral_sub hA hmB, intervalIntegral.integral_const_mul,
      intervalIntegral.integral_const_mul, intervalIntegral.integral_sub hC hmD,
      intervalIntegral.integral_const_mul]
  have hzero : (∫ x in (0:ℝ)..1, x * f x) - m * (∫ x in (0:ℝ)..1, f x) = 0 := by
    rw [hG]
    ring
  have hKmH : 0 < (∫ x in (0:ℝ)..1, x * f x ^ 2) - m * (∫ x in (0:ℝ)..1, f x ^ 2) := by
    have h := hP
    rw [hkey, hzero, mul_zero, sub_zero] at h
    exact h
  rw [← hm, lt_div_iff₀ hH]
  linarith

#print axioms putnam_2025_b2
