import Mathlib

open Finset

open Topology Filter Nat
-- Note: uses (ℝ → ℝ) instead of (Set.Icc (1 : ℝ) 3 → ℝ)
noncomputable abbrev putnam_2014_b2_solution : ℝ := Real.log (4 / 3)

theorem putnam_2014_b2 :
  IsGreatest {t | ∃ f : ℝ → ℝ,
    (∀ x : Set.Icc (1 : ℝ) 3, -1 ≤ f x ∧ f x ≤ 1) ∧
    (∫ x in Set.Ioo 1 3, f x = 0) ∧
    (∫ x in Set.Ioo 1 3, (f x) / x) = t}
  putnam_2014_b2_solution := by
  have hIoo : ∀ g : ℝ → ℝ, ∫ x in Set.Ioo (1:ℝ) 3, g x = ∫ x in (1:ℝ)..3, g x := by
    intro g
    rw [intervalIntegral.integral_of_le (show (1:ℝ) ≤ 3 by norm_num),
      MeasureTheory.integral_Ioc_eq_integral_Ioo]
  have hlog : Real.log (4 / 3) = 2 * Real.log 2 - Real.log 3 := by
    have h4 : Real.log (4 / 3) = Real.log 4 - Real.log 3 :=
      Real.log_div (by norm_num) (by norm_num)
    have h4' : Real.log 4 = 2 * Real.log 2 := by
      rw [show (4:ℝ) = 2 ^ 2 by norm_num, Real.log_pow]
      norm_num
    rw [h4, h4']
  have hinv : ∀ a b : ℝ, 0 < a → a ≤ b →
      IntervalIntegrable (fun x : ℝ => x⁻¹) MeasureTheory.volume a b := by
    intro a b ha hab
    apply ContinuousOn.intervalIntegrable
    intro x hx
    rw [Set.uIcc_of_le hab] at hx
    exact (continuousAt_inv₀ (show x ≠ 0 from (show (0:ℝ) < x by linarith [hx.1]).ne')).continuousWithinAt
  have hinv12 : ∫ x in (1:ℝ)..2, x⁻¹ = Real.log 2 := by
    rw [integral_inv_of_pos (show (0:ℝ) < 1 by norm_num) (show (0:ℝ) < 2 by norm_num)]
    norm_num
  have hinv23 : ∫ x in (2:ℝ)..3, x⁻¹ = Real.log 3 - Real.log 2 := by
    rw [integral_inv_of_pos (show (0:ℝ) < 2 by norm_num) (show (0:ℝ) < 3 by norm_num),
      Real.log_div (show (3:ℝ) ≠ 0 by norm_num) (show (2:ℝ) ≠ 0 by norm_num)]
  have hcongr : ∀ (a b : ℝ) (g h : ℝ → ℝ), a ≤ b →
      IntervalIntegrable g MeasureTheory.volume a b →
      (∀ x ∈ Set.Ioc a b, g x = h x) →
      IntervalIntegrable h MeasureTheory.volume a b := by
    intro a b g h hab hg hgh
    refine intervalIntegrable_iff.2 ((intervalIntegrable_iff.1 hg).congr_fun ?_ measurableSet_uIoc)
    intro x hx
    rw [Set.uIoc_of_le hab] at hx
    exact hgh x hx
  -- membership pieces for the witness f = if x ≤ 2 then 1 else -1
  have hF12 : IntervalIntegrable (fun x : ℝ => if x ≤ 2 then (1:ℝ) else -1)
      MeasureTheory.volume 1 2 :=
    hcongr 1 2 (fun _ => (1:ℝ)) (fun x : ℝ => if x ≤ 2 then (1:ℝ) else -1)
      (by norm_num) intervalIntegrable_const (fun x hx => (if_pos hx.2).symm)
  have hF23 : IntervalIntegrable (fun x : ℝ => if x ≤ 2 then (1:ℝ) else -1)
      MeasureTheory.volume 2 3 :=
    hcongr 2 3 (fun _ => (-1:ℝ)) (fun x : ℝ => if x ≤ 2 then (1:ℝ) else -1)
      (by norm_num) intervalIntegrable_const (fun x hx => (if_neg (not_le.mpr hx.1)).symm)
  have hz : ∫ x in (1:ℝ)..3, (if x ≤ 2 then (1:ℝ) else -1) = 0 := by
    rw [← intervalIntegral.integral_add_adjacent_intervals hF12 hF23]
    have e1 : ∫ x in (1:ℝ)..2, (if x ≤ 2 then (1:ℝ) else -1) = ∫ _ in (1:ℝ)..2, (1:ℝ) :=
      intervalIntegral.integral_congr (fun x hx => by
        rw [Set.uIcc_of_le (show (1:ℝ) ≤ 2 by norm_num)] at hx
        show (if x ≤ 2 then (1:ℝ) else -1) = 1
        rw [if_pos hx.2])
    have e2 : ∫ x in (2:ℝ)..3, (if x ≤ 2 then (1:ℝ) else -1) = ∫ _ in (2:ℝ)..3, (-1:ℝ) :=
      intervalIntegral.integral_congr_ae (MeasureTheory.ae_of_all _ (fun x hx => by
        rw [Set.uIoc_of_le (show (2:ℝ) ≤ 3 by norm_num)] at hx
        show (if x ≤ 2 then (1:ℝ) else -1) = -1
        rw [if_neg (not_le.mpr hx.1)]))
    rw [e1, e2]
    norm_num [intervalIntegral.integral_const]
  -- value pieces for f(x)/x
  have hG12 : IntervalIntegrable (fun x : ℝ => (if x ≤ 2 then (1:ℝ) else -1) / x)
      MeasureTheory.volume 1 2 :=
    hcongr 1 2 (fun x : ℝ => x⁻¹) (fun x : ℝ => (if x ≤ 2 then (1:ℝ) else -1) / x)
      (by norm_num) (hinv 1 2 (by norm_num) (by norm_num))
      (fun x hx => by
        show x⁻¹ = (if x ≤ 2 then (1:ℝ) else -1) / x
        rw [if_pos hx.2]
        ring)
  have hG23 : IntervalIntegrable (fun x : ℝ => (if x ≤ 2 then (1:ℝ) else -1) / x)
      MeasureTheory.volume 2 3 :=
    hcongr 2 3 (fun x : ℝ => -x⁻¹) (fun x : ℝ => (if x ≤ 2 then (1:ℝ) else -1) / x)
      (by norm_num) ((hinv 2 3 (by norm_num) (by norm_num)).neg)
      (fun x hx => by
        show -x⁻¹ = (if x ≤ 2 then (1:ℝ) else -1) / x
        rw [if_neg (not_le.mpr hx.1)]
        ring)
  have hval : ∫ x in (1:ℝ)..3, (if x ≤ 2 then (1:ℝ) else -1) / x
      = 2 * Real.log 2 - Real.log 3 := by
    rw [← intervalIntegral.integral_add_adjacent_intervals hG12 hG23]
    have e1 : ∫ x in (1:ℝ)..2, (if x ≤ 2 then (1:ℝ) else -1) / x
        = ∫ x in (1:ℝ)..2, x⁻¹ :=
      intervalIntegral.integral_congr (fun x hx => by
        rw [Set.uIcc_of_le (show (1:ℝ) ≤ 2 by norm_num)] at hx
        show (if x ≤ 2 then (1:ℝ) else -1) / x = x⁻¹
        rw [if_pos hx.2]
        ring)
    have e2 : ∫ x in (2:ℝ)..3, (if x ≤ 2 then (1:ℝ) else -1) / x
        = ∫ x in (2:ℝ)..3, -x⁻¹ :=
      intervalIntegral.integral_congr_ae (MeasureTheory.ae_of_all _ (fun x hx => by
        rw [Set.uIoc_of_le (show (2:ℝ) ≤ 3 by norm_num)] at hx
        show (if x ≤ 2 then (1:ℝ) else -1) / x = -x⁻¹
        rw [if_neg (not_le.mpr hx.1)]
        ring))
    rw [e1, e2, intervalIntegral.integral_neg, hinv12, hinv23]
    ring
  constructor
  · rw [Set.mem_setOf_eq]
    refine ⟨fun x => if x ≤ 2 then 1 else -1, ?_, ?_, ?_⟩
    · rintro ⟨x, hx⟩
      show -1 ≤ (if x ≤ 2 then (1:ℝ) else -1) ∧ (if x ≤ 2 then (1:ℝ) else -1) ≤ 1
      split_ifs <;> norm_num
    · exact (hIoo _).trans hz
    · exact (hIoo _).trans (hval.trans hlog.symm)
  · intro t ht
    obtain ⟨f, hbd, hzero, hteq⟩ := ht
    subst hteq
    show _ ≤ Real.log (4 / 3)
    have hb : ∀ x ∈ Set.Ioo (1:ℝ) 3, -1 ≤ f x ∧ f x ≤ 1 :=
      fun x hx => hbd ⟨x, Set.Ioo_subset_Icc_self hx⟩
    by_cases hg : MeasureTheory.Integrable (fun x => f x / x)
        (MeasureTheory.volume.restrict (Set.Ioo (1:ℝ) 3))
    · have hmeasf : MeasureTheory.AEStronglyMeasurable f
          (MeasureTheory.volume.restrict (Set.Ioo (1:ℝ) 3)) := by
        refine (hg.aestronglyMeasurable.mul
          (measurable_id.aestronglyMeasurable
            (μ := MeasureTheory.volume.restrict (Set.Ioo (1:ℝ) 3)))).congr ?_
        filter_upwards [MeasureTheory.ae_restrict_mem measurableSet_Ioo] with x hx
        have hx0 : x ≠ 0 := (show (0:ℝ) < x by linarith [hx.1]).ne'
        show f x / x * x = f x
        field_simp
      haveI : MeasureTheory.IsFiniteMeasure
          (MeasureTheory.volume.restrict (Set.Ioo (1:ℝ) 3)) := ⟨by simp⟩
      have hfint : MeasureTheory.Integrable f
          (MeasureTheory.volume.restrict (Set.Ioo (1:ℝ) 3)) := by
        refine MeasureTheory.Integrable.mono' (MeasureTheory.integrable_const (1:ℝ)) hmeasf ?_
        filter_upwards [MeasureTheory.ae_restrict_mem measurableSet_Ioo] with x hx
        show ‖f x‖ ≤ 1
        rw [Real.norm_eq_abs]
        exact abs_le.2 (hb x hx)
      have hgsub : MeasureTheory.Integrable (fun x => f x / x - f x / 2)
          (MeasureTheory.volume.restrict (Set.Ioo (1:ℝ) 3)) :=
        hg.sub (hfint.div_const 2)
      have hdiv2 : ∫ x in Set.Ioo (1:ℝ) 3, f x / 2 = 0 := by
        rw [MeasureTheory.integral_div, hzero, zero_div]
      have hsub : ∫ x in Set.Ioo (1:ℝ) 3, (f x / x - f x / 2)
          = ∫ x in Set.Ioo (1:ℝ) 3, f x / x := by
        rw [MeasureTheory.integral_sub hg (hfint.div_const 2), hdiv2, sub_zero]
      have pw : ∀ x ∈ Set.Ioo (1:ℝ) 3, f x / x - f x / 2 ≤
          (if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹) := by
        intro x hx
        obtain ⟨hf1, hf2⟩ := hb x hx
        have hxpos : (0:ℝ) < x := by linarith [hx.1]
        have e : f x / x - f x / 2 = f x * (x⁻¹ - 1 / 2) := by ring
        rw [e]
        by_cases hx2 : x ≤ 2
        · rw [if_pos hx2]
          have hu : (1 / 2 : ℝ) ≤ x⁻¹ := by
            rw [← one_div]
            exact one_div_le_one_div_of_le hxpos hx2
          nlinarith [mul_nonneg (sub_nonneg.2 hf2) (sub_nonneg.2 hu)]
        · rw [if_neg hx2]
          have hx2' : (2:ℝ) ≤ x := le_of_lt (not_le.mp hx2)
          have hu : x⁻¹ ≤ 1 / 2 := by
            rw [← one_div]
            exact one_div_le_one_div_of_le (by norm_num) hx2'
          nlinarith [mul_nonneg (by linarith : (0:ℝ) ≤ f x + 1)
            (by linarith : (0:ℝ) ≤ 1 / 2 - x⁻¹)]
      have hc12 : IntervalIntegrable (fun _ : ℝ => (1 / 2 : ℝ)) MeasureTheory.volume 1 2 :=
        intervalIntegrable_const
      have hc23 : IntervalIntegrable (fun _ : ℝ => (1 / 2 : ℝ)) MeasureTheory.volume 2 3 :=
        intervalIntegrable_const
      have hH12 : IntervalIntegrable (fun x : ℝ => if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
          MeasureTheory.volume 1 2 :=
        hcongr 1 2 (fun x : ℝ => x⁻¹ - 1 / 2)
          (fun x : ℝ => if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
          (by norm_num) ((hinv 1 2 (by norm_num) (by norm_num)).sub hc12)
          (fun x hx => (if_pos hx.2).symm)
      have hH23 : IntervalIntegrable (fun x : ℝ => if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
          MeasureTheory.volume 2 3 :=
        hcongr 2 3 (fun x : ℝ => 1 / 2 - x⁻¹)
          (fun x : ℝ => if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
          (by norm_num) (hc23.sub (hinv 2 3 (by norm_num) (by norm_num)))
          (fun x hx => (if_neg (not_le.mpr hx.1)).symm)
      have hH13 := hH12.trans hH23
      have hHint : MeasureTheory.Integrable
          (fun x : ℝ => if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
          (MeasureTheory.volume.restrict (Set.Ioo (1:ℝ) 3)) :=
        MeasureTheory.IntegrableOn.mono_set
          ((intervalIntegrable_iff_integrableOn_Ioc_of_le (show (1:ℝ) ≤ 3 by norm_num)).1 hH13)
          Set.Ioo_subset_Ioc_self
      have hHval : ∫ x in (1:ℝ)..3, (if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
          = Real.log (4 / 3) := by
        rw [← intervalIntegral.integral_add_adjacent_intervals hH12 hH23]
        have e1 : ∫ x in (1:ℝ)..2, (if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
            = ∫ x in (1:ℝ)..2, (x⁻¹ - 1 / 2) :=
          intervalIntegral.integral_congr (fun x hx => by
            rw [Set.uIcc_of_le (show (1:ℝ) ≤ 2 by norm_num)] at hx
            show (if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹) = x⁻¹ - 1 / 2
            rw [if_pos hx.2])
        have e2 : ∫ x in (2:ℝ)..3, (if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
            = ∫ x in (2:ℝ)..3, (1 / 2 - x⁻¹) :=
          intervalIntegral.integral_congr_ae (MeasureTheory.ae_of_all _ (fun x hx => by
            rw [Set.uIoc_of_le (show (2:ℝ) ≤ 3 by norm_num)] at hx
            show (if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹) = 1 / 2 - x⁻¹
            rw [if_neg (not_le.mpr hx.1)]))
        rw [e1, e2, intervalIntegral.integral_sub (hinv 1 2 (by norm_num) (by norm_num)) hc12,
          intervalIntegral.integral_sub hc23 (hinv 2 3 (by norm_num) (by norm_num)),
          hinv12, hinv23, hlog]
        simp only [intervalIntegral.integral_const, smul_eq_mul]
        ring
      have hHI : ∫ x in Set.Ioo (1:ℝ) 3, (if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹)
          = Real.log (4 / 3) :=
        (hIoo _).trans hHval
      have hkey : ∫ x in Set.Ioo (1:ℝ) 3, (f x / x - f x / 2)
          ≤ ∫ x in Set.Ioo (1:ℝ) 3, (if x ≤ 2 then x⁻¹ - 1 / 2 else 1 / 2 - x⁻¹) :=
        MeasureTheory.integral_mono_ae hgsub hHint
          ((MeasureTheory.ae_restrict_mem measurableSet_Ioo).mono fun x hx => pw x hx)
      rw [hsub] at hkey
      exact hkey.trans hHI.le
    · rw [MeasureTheory.integral_undef hg]
      exact (Real.log_pos (show (1:ℝ) < 4 / 3 by norm_num)).le

#print axioms putnam_2014_b2
