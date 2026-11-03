import Mathlib

open Finset

abbrev putnam_2010_a2_solution : Set (ℝ → ℝ) := {f : ℝ → ℝ | ∃ c d : ℝ, ∀ x : ℝ, f x = c*x + d}

theorem putnam_2010_a2 : {f : ℝ → ℝ | Differentiable ℝ f ∧
∀ x : ℝ, ∀ n : ℤ, n > 0 → deriv f x = (f (x + n) - f x)/n} = putnam_2010_a2_solution := by
  ext f
  simp only [Set.mem_setOf_eq, putnam_2010_a2_solution]
  constructor
  · rintro ⟨hf, h⟩
    have hstep1 : ∀ x, deriv f x = f (x + 1) - f x := by
      intro x
      have h1 := h x 1 (by norm_num)
      norm_num at h1
      linarith
    have hper : ∀ x, deriv f (x + 1) = deriv f x := by
      intro x
      have e1 := hstep1 x
      have e2 : deriv f (x + 1) = f (x + 2) - f (x + 1) := by
        have := hstep1 (x + 1)
        rw [show x + 1 + 1 = x + 2 by ring] at this
        exact this
      have e3 := h x 2 (by norm_num)
      norm_num at e3
      linarith
    have hFc : Continuous (deriv f) := by
      have hfc : Continuous f := hf.continuous
      have hF : deriv f = fun x => f (x + 1) - f x := funext hstep1
      rw [hF]
      fun_prop
    have hperF : Function.Periodic (deriv f) 1 := by
      intro x
      exact hper x
    have hint : ∀ x, deriv f x = ∫ t in x..x+1, deriv f t := by
      intro x
      rw [intervalIntegral.integral_eq_sub_of_hasDerivAt (fun y _ => (hf y).hasDerivAt)
        (hFc.intervalIntegrable _ _)]
      exact hstep1 x
    have hconst : ∀ x, deriv f x = deriv f 0 := by
      intro x
      have hx : ∫ t in x..x+1, deriv f t = ∫ t in (0:ℝ)..0+1, deriv f t :=
        hperF.intervalIntegral_add_eq x 0
      have hx0 := hint x
      have h00 := hint 0
      linarith
    have hgdiff : Differentiable ℝ (fun y => f y - deriv f 0 * y) := by
      intro x
      exact ((hf x).hasDerivAt.sub ((hasDerivAt_id' x).const_mul (deriv f 0))).differentiableAt
    have hgderiv : ∀ x, deriv (fun y => f y - deriv f 0 * y) x = 0 := by
      intro x
      have hh := (hf x).hasDerivAt.sub ((hasDerivAt_id' x).const_mul (deriv f 0))
      refine hh.deriv.trans ?_
      rw [hconst x]
      ring
    have key : ∀ x, f x = deriv f 0 * x + f 0 := by
      intro x
      have hk : f x - deriv f 0 * x = f 0 - deriv f 0 * 0 :=
        is_const_of_deriv_eq_zero hgdiff hgderiv x 0
      linarith
    exact ⟨deriv f 0, f 0, key⟩
  · rintro ⟨c, d, hcd⟩
    have hfeq : f = fun x => c * x + d := funext hcd
    subst hfeq
    refine ⟨by fun_prop, ?_⟩
    intro x n hn
    have hn0 : (n:ℝ) ≠ 0 := by
      have : (0:ℝ) < n := by exact_mod_cast hn
      exact this.ne'
    have hd : deriv (fun x : ℝ => c * x + d) x = c :=
      (((hasDerivAt_id' x).const_mul c).add_const d).deriv.trans (by ring)
    show deriv (fun x : ℝ => c * x + d) x = (c * (x + n) + d - (c * x + d)) / n
    rw [hd, eq_div_iff hn0]
    ring

#print axioms putnam_2010_a2
