import Mathlib

open Finset

open Set Topology Filter
noncomputable abbrev putnam_2006_b6_solution : ℕ → ℝ := fun k => ((k+1)/k)^k

theorem pu_bern_upper (k : ℕ) (hk : k > 1) (x h : ℝ) (hx : 0 < x) (hh : 0 ≤ h) :
    (x + h) ^ (1 + (1:ℝ)/k) - x ^ (1 + (1:ℝ)/k) ≤ (1 + (1:ℝ)/k) * h * (x + h) ^ ((1:ℝ)/k) := by
  have hk1 : (1:ℝ) < k := by exact_mod_cast hk
  have hk0 : (0:ℝ) < k := by linarith
  have hkinv : (0:ℝ) ≤ 1/k := by positivity
  have hxh : 0 < x + h := by linarith
  have hp : (1:ℝ) ≤ 1 + 1/k := by linarith
  have h1 : h/(x+h) ≤ 1 := by
    rw [div_le_iff₀ hxh]
    linarith
  have hs : -1 ≤ -h/(x+h) := by
    rw [neg_div]
    linarith
  have hb := one_add_mul_self_le_rpow_one_add hs hp
  have hsum' : x/(x+h) + h/(x+h) = 1 := by
    rw [← add_div, div_self hxh.ne']
  have hbase : 1 + -h/(x+h) = x/(x+h) := by
    rw [neg_div]
    linarith
  rw [hbase, Real.div_rpow hx.le hxh.le] at hb
  have hpos : 0 < (x + h) ^ (1 + (1:ℝ)/k) := Real.rpow_pos_of_pos hxh _
  have hsub : (x + h) ^ (1 + (1:ℝ)/k) / (x + h) = (x + h) ^ ((1:ℝ)/k) := by
    rw [← Real.rpow_sub_one hxh.ne']
    congr 1
    ring
  rw [le_div_iff₀ hpos] at hb
  have e : (1 + (1 + (1:ℝ)/k) * (-h/(x+h))) * (x + h) ^ (1 + (1:ℝ)/k)
      = (x + h) ^ (1 + (1:ℝ)/k)
        - (1 + (1:ℝ)/k) * h * ((x + h) ^ (1 + (1:ℝ)/k) / (x + h)) := by
    ring
  rw [hsub] at e
  linarith

theorem pu_bern_lower (k : ℕ) (hk : k > 1) (x h : ℝ) (hx : 0 < x) (hh : 0 ≤ h) :
    (1 + (1:ℝ)/k) * h * x ^ ((1:ℝ)/k) ≤ (x + h) ^ (1 + (1:ℝ)/k) - x ^ (1 + (1:ℝ)/k) := by
  have hk1 : (1:ℝ) < k := by exact_mod_cast hk
  have hk0 : (0:ℝ) < k := by linarith
  have hkinv : (0:ℝ) ≤ 1/k := by positivity
  have hxh : 0 < x + h := by linarith
  have hp : (1:ℝ) ≤ 1 + 1/k := by linarith
  have hs : -1 ≤ h/x := by
    have : 0 ≤ h/x := div_nonneg hh hx.le
    linarith
  have hb := one_add_mul_self_le_rpow_one_add hs hp
  have hbase : 1 + h/x = (x + h)/x := by
    rw [add_div, div_self hx.ne']
  rw [hbase, Real.div_rpow hxh.le hx.le] at hb
  have hpos : 0 < x ^ (1 + (1:ℝ)/k) := Real.rpow_pos_of_pos hx _
  have hsub : x ^ (1 + (1:ℝ)/k) / x = x ^ ((1:ℝ)/k) := by
    rw [← Real.rpow_sub_one hx.ne']
    congr 1
    ring
  rw [le_div_iff₀ hpos] at hb
  have e : (1 + (1 + (1:ℝ)/k) * (h/x)) * x ^ (1 + (1:ℝ)/k)
      = x ^ (1 + (1:ℝ)/k) + (1 + (1:ℝ)/k) * h * (x ^ (1 + (1:ℝ)/k) / x) := by
    ring
  rw [hsub] at e
  linarith

theorem putnam_2006_b6 (k : ℕ)
(hk : k > 1)
(a : ℕ → ℝ)
(ha0 : a 0 > 0)
(ha : ∀ n : ℕ, a (n + 1) = a n + 1/((a n)^((1 : ℝ)/k)))
: Tendsto (fun n => (a n)^(k+1)/(n ^ k)) atTop (𝓝 (putnam_2006_b6_solution k)) := by
  have hk1 : (1:ℝ) < k := by exact_mod_cast hk
  have hk0 : (0:ℝ) < k := by linarith
  have hkinv : (0:ℝ) ≤ 1/k := by positivity
  have hP0 : (0:ℝ) < 1 + 1/k := by linarith
  have hc : (1:ℝ) + 1/k = ((k:ℝ)+1)/k := by
    rw [add_div, div_self hk0.ne']
  -- positivity of the sequence
  have hapos : ∀ n : ℕ, 0 < a n := by
    intro n
    induction n with
    | zero => exact ha0
    | succ m ih =>
      rw [ha m]
      exact add_pos ih (one_div_pos.mpr (Real.rpow_pos_of_pos ih _))
  -- lower bound on increments
  have hlow : ∀ n : ℕ, (1 + (1:ℝ)/k) ≤ (a (n+1))^(1 + (1:ℝ)/k) - (a n)^(1 + (1:ℝ)/k) := by
    intro n
    have hx := hapos n
    have ht : 0 < (a n)^((1:ℝ)/k) := Real.rpow_pos_of_pos hx _
    have h0 : (0:ℝ) ≤ 1 / (a n)^((1:ℝ)/k) := by positivity
    have hl := pu_bern_lower k hk (a n) (1 / (a n)^((1:ℝ)/k)) hx h0
    have hcancel : (1 + (1:ℝ)/k) * (1 / (a n)^((1:ℝ)/k)) * (a n)^((1:ℝ)/k)
        = 1 + (1:ℝ)/k := by
      rw [mul_assoc, one_div_mul_cancel ht.ne', mul_one]
    rw [ha n]
    linarith
  -- linear growth of y n = a n ^ P
  have hy_lin : ∀ n : ℕ, (a 0)^(1 + (1:ℝ)/k) + (n:ℝ) * (1 + (1:ℝ)/k)
      ≤ (a n)^(1 + (1:ℝ)/k) := by
    intro n
    induction n with
    | zero => simp
    | succ m ih =>
      have hm := hlow m
      push_cast
      rw [add_mul, one_mul]
      linarith
  have hyinf : Tendsto (fun n : ℕ => (a n)^(1 + (1:ℝ)/k)) atTop atTop := by
    refine tendsto_atTop_mono (fun n => ?_) (tendsto_natCast_atTop_atTop.atTop_mul_const hP0)
    show (n:ℝ) * (1 + (1:ℝ)/k) ≤ (a n)^(1 + (1:ℝ)/k)
    have h1 := hy_lin n
    have h2 := Real.rpow_nonneg (hapos 0).le (1 + (1:ℝ)/k)
    linarith
  have hinv : Tendsto (fun n : ℕ => 1 / (a n)^(1 + (1:ℝ)/k)) atTop (𝓝 0) := by
    refine (tendsto_inv_atTop_zero.comp hyinf).congr (fun n => ?_)
    simp [one_div]
  have h1 : Tendsto (fun n : ℕ => 1 + 1 / (a n)^(1 + (1:ℝ)/k)) atTop (𝓝 1) := by
    have h : Tendsto (fun n : ℕ => (1:ℝ) + 1 / (a n)^(1 + (1:ℝ)/k)) atTop (𝓝 ((1:ℝ) + 0)) :=
      tendsto_const_nhds.add hinv
    rw [add_zero] at h
    exact h
  have hU : Tendsto (fun n : ℕ => (1 + (1:ℝ)/k) * (1 + 1 / (a n)^(1 + (1:ℝ)/k))^((1:ℝ)/k))
      atTop (𝓝 (1 + (1:ℝ)/k)) := by
    have h2 := (h1.rpow_const (Or.inr hkinv)).const_mul (1 + (1:ℝ)/k)
    rw [Real.one_rpow, mul_one] at h2
    exact h2
  -- upper bound on increments
  have hup : ∀ n : ℕ, (a (n+1))^(1 + (1:ℝ)/k) - (a n)^(1 + (1:ℝ)/k)
      ≤ (1 + (1:ℝ)/k) * (1 + 1 / (a n)^(1 + (1:ℝ)/k))^((1:ℝ)/k) := by
    intro n
    have hx := hapos n
    have ht : 0 < (a n)^((1:ℝ)/k) := Real.rpow_pos_of_pos hx _
    have h0 : (0:ℝ) ≤ 1 / (a n)^((1:ℝ)/k) := by positivity
    have hD := pu_bern_upper k hk (a n) (1 / (a n)^((1:ℝ)/k)) hx h0
    have hs0 : 0 < a n + 1 / (a n)^((1:ℝ)/k) := by linarith
    have F1 : (a n)^((1:ℝ)/k) * a n = (a n)^(1 + (1:ℝ)/k) := by
      conv_rhs => rw [show (1 + (1:ℝ)/k) = (1:ℝ)/k + 1 by ring]
      rw [Real.rpow_add hx, Real.rpow_one]
    have F3 : (a n + 1 / (a n)^((1:ℝ)/k)) / a n = 1 + 1 / (a n)^(1 + (1:ℝ)/k) := by
      rw [← F1, add_div, div_self hx.ne', div_div]
    have hrat : (1 + 1 / (a n)^(1 + (1:ℝ)/k))^((1:ℝ)/k)
        = (a n + 1 / (a n)^((1:ℝ)/k))^((1:ℝ)/k) / (a n)^((1:ℝ)/k) := by
      rw [← F3, Real.div_rpow hs0.le hx.le]
    have hm : (1 + (1:ℝ)/k) * (1 / (a n)^((1:ℝ)/k)) * (a n + 1 / (a n)^((1:ℝ)/k))^((1:ℝ)/k)
        = (1 + (1:ℝ)/k) * ((a n + 1 / (a n)^((1:ℝ)/k))^((1:ℝ)/k) / (a n)^((1:ℝ)/k)) := by
      ring
    rw [ha n, hrat]
    linarith
  have hDlim : Tendsto (fun n : ℕ => (a (n+1))^(1 + (1:ℝ)/k) - (a n)^(1 + (1:ℝ)/k))
      atTop (𝓝 (1 + (1:ℝ)/k)) :=
    tendsto_of_tendsto_of_tendsto_of_le_of_le tendsto_const_nhds hU
      (by intro n; exact hlow n) (by intro n; exact hup n)
  -- Cesàro
  have hsum : ∀ n : ℕ, ∑ i ∈ range n, ((a (i+1))^(1 + (1:ℝ)/k) - (a i)^(1 + (1:ℝ)/k))
      = (a n)^(1 + (1:ℝ)/k) - (a 0)^(1 + (1:ℝ)/k) :=
    fun n => Finset.sum_range_sub (fun i => (a i)^(1 + (1:ℝ)/k)) n
  have hces2 : Tendsto (fun n : ℕ => (n:ℝ)⁻¹ * ((a n)^(1 + (1:ℝ)/k) - (a 0)^(1 + (1:ℝ)/k)))
      atTop (𝓝 (1 + (1:ℝ)/k)) :=
    hDlim.cesaro.congr (fun n => by simp only [hsum n])
  have hq : Tendsto (fun n : ℕ => (a n)^(1 + (1:ℝ)/k) / (n:ℝ)) atTop (𝓝 (1 + (1:ℝ)/k)) := by
    have h4 := hces2.add (tendsto_const_div_atTop_nhds_zero_nat ((a 0)^(1 + (1:ℝ)/k)))
    rw [add_zero] at h4
    refine h4.congr (fun n => ?_)
    show (n:ℝ)⁻¹ * ((a n)^(1 + (1:ℝ)/k) - (a 0)^(1 + (1:ℝ)/k))
        + (a 0)^(1 + (1:ℝ)/k) / (n:ℝ) = (a n)^(1 + (1:ℝ)/k) / (n:ℝ)
    ring
  -- identify a n ^ (k+1) with (a n ^ P) ^ k
  have hyk : ∀ n : ℕ, ((a n)^(1 + (1:ℝ)/k))^k = (a n)^(k+1) := by
    intro n
    have hx := hapos n
    rw [← Real.rpow_natCast, ← Real.rpow_mul hx.le]
    rw [show (1 + (1:ℝ)/k) * (k:ℝ) = ((k+1 : ℕ):ℝ) by
      push_cast
      rw [add_mul, one_mul, one_div, inv_mul_cancel₀ hk0.ne']]
    rw [Real.rpow_natCast]
  have key : Tendsto (fun n : ℕ => (a n)^(k+1) / ((n:ℝ)^k)) atTop (𝓝 ((1 + (1:ℝ)/k)^k)) := by
    refine (hq.pow k).congr (fun n => ?_)
    rw [div_pow, hyk n]
  have hsol : putnam_2006_b6_solution k = (1 + (1:ℝ)/k)^k := by
    unfold putnam_2006_b6_solution
    rw [hc]
  rw [hsol]
  exact key

#print axioms putnam_2006_b6
