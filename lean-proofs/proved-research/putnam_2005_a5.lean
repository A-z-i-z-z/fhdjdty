import Mathlib

open Finset

open Nat Set
noncomputable abbrev putnam_2005_a5_solution : ℝ := Real.pi * (Real.log 2) / 8

theorem putnam2005a5_h1 (t : ℝ) : (1:ℝ) + t^2 ≠ 0 := by positivity

theorem putnam2005a5_h2 (t : ℝ) : t^2 + 1 ≠ 0 := by positivity

theorem putnam2005a5_h3 (t : ℝ) : |t| + 1 ≠ 0 := by positivity

theorem putnam2005a5_G_cont :
    Continuous (fun y : ℝ => Real.log (|y| + 1) / (y^2 + 1)) :=
  Continuous.div (Continuous.log (by fun_prop) putnam2005a5_h3) (by fun_prop) putnam2005a5_h2

theorem putnam2005a5_atan : ∫ t in (0:ℝ)..1, (1:ℝ) / (1 + t^2) = Real.pi / 4 := by
  have hc : Continuous (fun t : ℝ => (1:ℝ) / (1 + t^2)) :=
    Continuous.div continuous_const (by fun_prop) putnam2005a5_h1
  rw [intervalIntegral.integral_eq_sub_of_hasDerivAt (f := Real.arctan)
    (fun x _ => Real.hasDerivAt_arctan x) (hc.intervalIntegrable 0 1)]
  simp [Real.arctan_one]

theorem putnam_2005_a5 :
  ∫ x in (0:ℝ)..1, (Real.log (x+1))/(x^2 + 1) = putnam_2005_a5_solution := by
  have hcase : ∀ t ∈ Set.uIcc (0:ℝ) 1, 0 ≤ t ∧ t ≤ 1 := by
    intro t ht
    rcases Set.mem_uIcc.mp ht with ⟨h1, h2⟩ | ⟨h1, h2⟩ <;> constructor <;> linarith
  have hpos : ∀ t ∈ Set.uIcc (0:ℝ) 1, 0 < 1 + t := by
    intro t ht
    obtain ⟨h1, _⟩ := hcase t ht
    linarith
  have hd : ∀ t ∈ Set.uIcc (0:ℝ) 1,
      HasDerivAt (fun s : ℝ => (1 - s) / (1 + s)) (-2 / (1 + t)^2) t := by
    intro t ht
    have h0 : (1 + t) ≠ 0 := (hpos t ht).ne'
    have hc : HasDerivAt (fun s : ℝ => 1 - s) (-1) t := (hasDerivAt_id t).const_sub 1
    have hdd : HasDerivAt (fun s : ℝ => 1 + s) 1 t := (hasDerivAt_id t).const_add 1
    have h := hc.div hdd h0
    convert h using 1
    first | ring | (dsimp only; ring) | (simp; ring)
  have hcont : ContinuousOn (fun s : ℝ => -2 / (1 + s)^2) (Set.uIcc (0:ℝ) 1) := by
    refine ContinuousOn.div continuousOn_const (Continuous.continuousOn ?_) ?_
    · fun_prop
    · intro s hs
      exact pow_ne_zero 2 (hpos s hs).ne'
  have hA : ∫ t in (0:ℝ)..1,
      Real.log (|(1 - t) / (1 + t)| + 1) / (((1 - t) / (1 + t))^2 + 1) * (-2 / (1 + t)^2)
      = ∫ x in (1:ℝ)..0, Real.log (|x| + 1) / (x^2 + 1) := by
    have h := intervalIntegral.integral_comp_mul_deriv hd hcont putnam2005a5_G_cont
    have e0 : ((1:ℝ) - 0) / (1 + 0) = 1 := by norm_num
    have e1 : ((1:ℝ) - 1) / (1 + 1) = 0 := by norm_num
    first
    | (rw [e0, e1] at h; exact h)
    | (simpa using h)
    | (norm_num at h ⊢; linarith)
  have hsym : ∫ x in (1:ℝ)..0, Real.log (|x| + 1) / (x^2 + 1)
      = -∫ x in (0:ℝ)..1, Real.log (|x| + 1) / (x^2 + 1) := by
    first
    | exact intervalIntegral.integral_symm _ _ _
    | exact intervalIntegral.integral_symm _ _
    | rw [intervalIntegral.integral_symm]
  have hGeq : ∫ x in (0:ℝ)..1, Real.log (|x| + 1) / (x^2 + 1)
      = ∫ x in (0:ℝ)..1, Real.log (x + 1) / (x^2 + 1) := by
    apply intervalIntegral.integral_congr
    intro x hx
    have h1 := (hcase x hx).1
    simp only [abs_of_nonneg h1]
  have hc1 : ContinuousOn (fun s : ℝ => Real.log (s + 1) / (s^2 + 1)) (Set.uIcc (0:ℝ) 1) := by
    refine ContinuousOn.div (ContinuousOn.log ?_ ?_) (Continuous.continuousOn ?_) ?_
    · fun_prop
    · intro s hs
      have h := hpos s hs
      have h' : (0:ℝ) < s + 1 := by linarith
      exact h'.ne'
    · fun_prop
    · intro s _
      exact putnam2005a5_h2 s
  have hc2 : ContinuousOn (fun s : ℝ => Real.log 2 * (1 / (1 + s^2))) (Set.uIcc (0:ℝ) 1) := by
    refine Continuous.continuousOn ?_
    refine continuous_const.mul (Continuous.div continuous_const ?_ putnam2005a5_h1)
    fun_prop
  have hI1 : IntervalIntegrable (fun s : ℝ => Real.log (s + 1) / (s^2 + 1))
      MeasureTheory.volume 0 1 := hc1.intervalIntegrable
  have hI2 : IntervalIntegrable (fun s : ℝ => Real.log 2 * (1 / (1 + s^2)))
      MeasureTheory.volume 0 1 := hc2.intervalIntegrable
  have hpt : ∫ t in (0:ℝ)..1,
      Real.log (|(1 - t) / (1 + t)| + 1) / (((1 - t) / (1 + t))^2 + 1) * (-2 / (1 + t)^2)
      = ∫ t in (0:ℝ)..1, (Real.log (t + 1) / (t^2 + 1) - Real.log 2 * (1 / (1 + t^2))) := by
    apply intervalIntegral.integral_congr
    intro t ht
    show Real.log (|(1 - t) / (1 + t)| + 1) / (((1 - t) / (1 + t))^2 + 1) * (-2 / (1 + t)^2)
      = Real.log (t + 1) / (t^2 + 1) - Real.log 2 * (1 / (1 + t^2))
    obtain ⟨h0, h1⟩ := hcase t ht
    have hne : (1 + t) ≠ 0 := (hpos t ht).ne'
    have hu : 0 ≤ (1 - t) / (1 + t) := div_nonneg (by linarith) (by linarith)
    rw [abs_of_nonneg hu]
    have h1' : (1 - t) / (1 + t) + 1 = 2 / (1 + t) := by
      first
      | (field_simp; ring)
      | field_simp
      | (rw [div_add_one hne]; congr 1; ring)
    have hlog : Real.log ((1 - t) / (1 + t) + 1) = Real.log 2 - Real.log (t + 1) := by
      rw [h1', Real.log_div two_ne_zero hne, add_comm (1:ℝ) t]
    have hsq : ((1 - t) / (1 + t))^2 + 1 = 2 * (t^2 + 1) / (1 + t)^2 := by
      first
      | (field_simp; ring)
      | field_simp
    have ht2 : t^2 + 1 ≠ 0 := putnam2005a5_h2 t
    rw [hlog, hsq]
    first
    | (field_simp; ring)
    | field_simp
  have hint : ∫ t in (0:ℝ)..1, (Real.log (t + 1) / (t^2 + 1) - Real.log 2 * (1 / (1 + t^2)))
      = (∫ t in (0:ℝ)..1, Real.log (t + 1) / (t^2 + 1)) - Real.log 2 * (Real.pi / 4) := by
    rw [intervalIntegral.integral_sub hI1 hI2, intervalIntegral.integral_const_mul,
      putnam2005a5_atan]
  show ∫ x in (0:ℝ)..1, (Real.log (x+1))/(x^2 + 1) = Real.pi * (Real.log 2) / 8
  linarith [hA, hsym, hGeq, hpt, hint]

#print axioms putnam_2005_a5
