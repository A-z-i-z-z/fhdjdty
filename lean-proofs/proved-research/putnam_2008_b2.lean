import Mathlib

open Finset

open Filter Topology Set Nat
abbrev putnam_2008_b2_solution : ℝ := -1

lemma putnam_2008_b2_closed_form (F : ℕ → ℝ → ℝ)
    (hF0 : ∀ x : ℝ, F 0 x = Real.log x)
    (hFn : ∀ n : ℕ, ∀ x > 0, F (n + 1) x = ∫ t in Set.Ioo 0 x, F n t) :
    ∀ n : ℕ, ∀ x : ℝ, x > 0 →
      F n x = x ^ n / (n.factorial : ℝ) *
        (Real.log x - ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1)) := by
  intro n
  induction n with
  | zero =>
    intro x _
    simp [hF0]
  | succ n ih =>
    intro x hx
    have hpos : (0:ℝ) ≤ x := hx.le
    have h1 : (n.factorial : ℝ) ≠ 0 := by positivity
    have h2 : ((n:ℝ) + 1) ≠ 0 := by positivity
    have h3 : (((n + 1).factorial : ℕ) : ℝ) ≠ 0 := by positivity
    rw [hFn n x hx]
    have hcongr : ∫ t in Set.Ioo (0:ℝ) x, F n t
        = ∫ t in Set.Ioo (0:ℝ) x, t ^ n / (n.factorial : ℝ) *
            (Real.log t - ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1)) := by
      apply MeasureTheory.setIntegral_congr_fun measurableSet_Ioo
      intro t ht
      exact ih t ht.1
    have hconv : ∫ t in Set.Ioo (0:ℝ) x, t ^ n / (n.factorial : ℝ) *
            (Real.log t - ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1))
        = ∫ t in (0:ℝ)..x, t ^ n / (n.factorial : ℝ) *
            (Real.log t - ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1)) := by
      rw [intervalIntegral.integral_of_le hpos, MeasureTheory.integral_Ioc_eq_integral_Ioo]
    have hderiv : ∀ t ∈ Set.Ioo (0:ℝ) x,
        HasDerivAt (fun s : ℝ => s ^ (n + 1) / ((n + 1).factorial : ℝ) *
            (Real.log s - ∑ i ∈ range (n + 1), (1 : ℝ) / ((i : ℝ) + 1)))
          (t ^ n / (n.factorial : ℝ) *
            (Real.log t - ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1))) t := by
      intro t ht
      have ht0 : t ≠ 0 := ne_of_gt ht.1
      have hlog : HasDerivAt Real.log t⁻¹ t := Real.hasDerivAt_log ht0
      have hp : HasDerivAt (fun s : ℝ => s ^ (n + 1)) (((n + 1 : ℕ) : ℝ) * t ^ n) t := by
        simpa using hasDerivAt_pow (n + 1) t
      have hG := (hp.div_const (((n + 1).factorial : ℕ) : ℝ)).mul
        (hlog.sub_const (∑ i ∈ range (n + 1), (1 : ℝ) / ((i : ℝ) + 1)))
      convert hG using 1
      simp only [Nat.factorial_succ, Finset.sum_range_succ]
      push_cast
      (try field_simp)
      (try ring)
    have hint : IntervalIntegrable
        (fun t : ℝ => t ^ n / (n.factorial : ℝ) *
          (Real.log t - ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1)))
        MeasureTheory.volume 0 x := by
      have hlogi : IntervalIntegrable Real.log MeasureTheory.volume 0 x := by
        first
        | exact intervalIntegral.intervalIntegrable_log'
        | exact intervalIntegrable_log'
      have hm : IntervalIntegrable (fun t : ℝ => Real.log t * t ^ n) MeasureTheory.volume 0 x :=
        hlogi.mul_continuousOn (continuous_pow n).continuousOn
      have e : (fun t : ℝ => t ^ n / (n.factorial : ℝ) *
          (Real.log t - ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1)))
          = fun t : ℝ => (1 / (n.factorial : ℝ)) * (Real.log t * t ^ n)
              - ((∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1)) / (n.factorial : ℝ)) * t ^ n := by
        funext t
        ring
      rw [e]
      exact (hm.const_mul _).sub (((continuous_pow n).intervalIntegrable 0 x).const_mul _)
    have hGc : Continuous (fun s : ℝ => s ^ (n + 1) / ((n + 1).factorial : ℝ) *
            (Real.log s - ∑ i ∈ range (n + 1), (1 : ℝ) / ((i : ℝ) + 1))) := by
      have e2 : (fun s : ℝ => s ^ (n + 1) / ((n + 1).factorial : ℝ) *
            (Real.log s - ∑ i ∈ range (n + 1), (1 : ℝ) / ((i : ℝ) + 1)))
          = fun s : ℝ => (1 / ((n + 1).factorial : ℝ)) * (s ^ n * (s * Real.log s))
              - ((∑ i ∈ range (n + 1), (1 : ℝ) / ((i : ℝ) + 1)) / ((n + 1).factorial : ℝ))
                  * s ^ (n + 1) := by
        funext s
        ring
      rw [e2]
      exact (((continuous_pow n).mul Real.continuous_mul_log).const_mul _).sub
        ((continuous_pow (n + 1)).const_mul _)
    have key : ∫ t in (0:ℝ)..x, t ^ n / (n.factorial : ℝ) *
            (Real.log t - ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1))
        = x ^ (n + 1) / ((n + 1).factorial : ℝ) *
            (Real.log x - ∑ i ∈ range (n + 1), (1 : ℝ) / ((i : ℝ) + 1)) := by
      rw [intervalIntegral.integral_eq_sub_of_hasDerivAt_of_le hpos hGc.continuousOn hderiv hint]
      simp
    rw [hcongr, hconv, key]

theorem putnam_2008_b2 (F : ℕ → ℝ → ℝ)
(hF0 : ∀ x : ℝ, F 0 x = Real.log x)
(hFn : ∀ n : ℕ, ∀ x > 0, F (n + 1) x = ∫ t in Set.Ioo 0 x, F n t)
: Tendsto (fun n : ℕ => ((n)! * F n 1) / Real.log n) atTop (𝓝 putnam_2008_b2_solution) := by
  have hclosed := putnam_2008_b2_closed_form F hF0 hFn
  have hval : ∀ n : ℕ, (n.factorial : ℝ) * F n 1
      = -(∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1)) := by
    intro n
    have h1 : (n.factorial : ℝ) ≠ 0 := by positivity
    rw [hclosed n 1 one_pos, one_pow, Real.log_one, zero_sub]
    (try field_simp)
    (try ring)
  have hH : ∀ n : ℕ, ((harmonic n : ℚ) : ℝ) = ∑ i ∈ range n, (1 : ℝ) / ((i : ℝ) + 1) := by
    intro n
    unfold harmonic
    push_cast
    (try simp [one_div])
  have hg : ∃ c : ℝ, Tendsto (fun n : ℕ => ((harmonic n : ℚ) : ℝ) - Real.log n) atTop (𝓝 c) :=
    ⟨_, by
      first
      | exact Real.tendsto_harmonic_sub_log
      | exact tendsto_harmonic_sub_log⟩
  obtain ⟨c, hc⟩ := hg
  have hlogtop : Tendsto (fun n : ℕ => Real.log n) atTop atTop :=
    Real.tendsto_log_atTop.comp tendsto_natCast_atTop_atTop
  have hdiv : Tendsto (fun n : ℕ => (((harmonic n : ℚ) : ℝ) - Real.log n) / Real.log n)
      atTop (𝓝 0) :=
    hc.div_atTop hlogtop
  have hlim : Tendsto (fun n : ℕ => -(1 + (((harmonic n : ℚ) : ℝ) - Real.log n) / Real.log n))
      atTop (𝓝 (-(1 + 0))) :=
    ((tendsto_const_nhds (x := (1:ℝ))).add hdiv).neg
  rw [add_zero] at hlim
  refine Tendsto.congr' ?_ hlim
  filter_upwards [eventually_ge_atTop 2] with n hn
  have hn2 : (2:ℝ) ≤ (n:ℝ) := by exact_mod_cast hn
  have hlogpos : 0 < Real.log n := Real.log_pos (by linarith)
  have hne : Real.log n ≠ 0 := ne_of_gt hlogpos
  rw [hval n, ← hH n]
  (try field_simp)
  (try ring)

#print axioms putnam_2008_b2
