import Mathlib

open Finset

open Filter Topology Set Nat
abbrev putnam_2008_b5_solution : Set (ℝ → ℝ) := {fun (x : ℝ) => x + n | n : ℤ} ∪ {fun (x : ℝ) => -x + n | n : ℤ}

theorem pb5_mul_den (q : ℚ) : q * (q.den : ℚ) = (q.num : ℚ) := by
  first
    | exact Rat.mul_den_eq_num q
    | (simp [Rat.mul_den_eq_num]; done)

theorem pb5_den_add_int (q : ℚ) (n : ℤ) : (q + (n:ℚ)).den = q.den := by
  first
    | (simp; done)
    | exact Nat.dvd_antisymm (by simpa using Rat.add_den_dvd q (n:ℚ)) (by simpa using Rat.add_den_dvd (q + (n:ℚ)) (-(n:ℚ)))

theorem pb5_mul_int (q : ℚ) (N : ℕ) (h : q.den ∣ N) : ∃ k : ℤ, q * (N:ℚ) = (k:ℚ) := by
  obtain ⟨t, ht⟩ := h
  refine ⟨q.num * (t:ℤ), ?_⟩
  rw [ht]
  push_cast
  rw [← pb5_mul_den]
  ring

theorem pb5_den_dvd (q : ℚ) (N : ℕ) (k : ℤ) (h : q * (N:ℚ) = (k:ℚ)) : q.den ∣ N := by
  have h1 : (q.num:ℚ) * (N:ℚ) = (k:ℚ) * (q.den:ℚ) := by
    rw [← pb5_mul_den, ← h]
    ring
  have h2 : q.num * (N:ℤ) = k * (q.den:ℤ) := by exact_mod_cast h1
  have h3 : (q.den:ℤ) ∣ q.num * (N:ℤ) := ⟨k, by rw [h2]; ring⟩
  have hcop : IsCoprime (q.den:ℤ) q.num := by
    rw [Int.isCoprime_iff_gcd_eq_one, Int.gcd_comm]
    first
      | exact q.reduced
      | simpa [Int.gcd] using q.reduced
  have h4 : (q.den:ℤ) ∣ (N:ℤ) := by
    first
      | exact hcop.dvd_of_dvd_mul_left h3
      | exact hcop.dvd_of_dvd_mul_right h3
      | exact hcop.symm.dvd_of_dvd_mul_left h3
      | exact hcop.symm.dvd_of_dvd_mul_right h3
  exact_mod_cast h4

theorem pb5_cast (q : ℚ) (N : ℕ) (k : ℤ) (h : q * (N:ℚ) = (k:ℚ)) :
    (q:ℝ) * (N:ℝ) = (k:ℝ) := by
  have h' := congrArg (fun z : ℚ => (z:ℝ)) h
  push_cast at h'
  linarith

theorem pb5_key (f : ℝ → ℝ) (hC : ContDiff ℝ 1 f)
    (hval : ∀ q : ℚ, ∃ p : ℚ, (p:ℝ) = f (q:ℝ) ∧ p.den = q.den)
    (x : ℚ) (m N : ℕ) (hm : 0 < m) (hN : N = x.den * m) :
    ∃ ξ : ℝ, (x:ℝ) < ξ ∧ ξ < (x:ℝ) + 1 / (N:ℝ) ∧ ∃ z : ℤ, deriv f ξ = (z:ℝ) := by
  have hdenpos : 0 < x.den := by
    first
      | exact x.den_pos
      | exact x.pos
      | exact Rat.den_pos x
  have hNpos : 0 < N := by
    rw [hN]
    exact Nat.mul_pos hdenpos hm
  have hNq : (N:ℚ) ≠ 0 := by exact_mod_cast hNpos.ne'
  have hNR : (0:ℝ) < (N:ℝ) := by exact_mod_cast hNpos
  have hAq : (x:ℚ) * (N:ℚ) = (x.num:ℚ) * (m:ℚ) := by
    rw [hN]
    push_cast
    rw [← pb5_mul_den x]
    ring
  obtain ⟨yq, hyq⟩ : ∃ yq : ℚ, yq = x + 1 / (N:ℚ) := ⟨_, rfl⟩
  have hyN : yq * (N:ℚ) = (x.num:ℚ) * (m:ℚ) + 1 := by
    rw [hyq, add_mul, one_div, inv_mul_cancel₀ hNq, hAq]
  have hyZ : ∃ k : ℤ, yq * (N:ℚ) = (k:ℚ) :=
    ⟨x.num * (m:ℤ) + 1, by exact_mod_cast hyN⟩
  obtain ⟨kyz, hkyz⟩ := hyZ
  have hyden : yq.den ∣ N := pb5_den_dvd yq N kyz hkyz
  obtain ⟨r, hr, hrd⟩ := hval yq
  obtain ⟨p, hp, hpd⟩ := hval x
  have hrdvd : r.den ∣ N := by
    rw [hrd]
    exact hyden
  have hpdvd : p.den ∣ N := by
    rw [hpd, hN]
    exact Nat.dvd_mul_right _ _
  obtain ⟨kr, hkr⟩ := pb5_mul_int r N hrdvd
  obtain ⟨kp, hkp⟩ := pb5_mul_int p N hpdvd
  have hrR : (r:ℝ) * (N:ℝ) = (kr:ℝ) := pb5_cast r N kr hkr
  have hpR : (p:ℝ) * (N:ℝ) = (kp:ℝ) := pb5_cast p N kp hkp
  have hyR : ((yq:ℚ):ℝ) = (x:ℝ) + 1 / (N:ℝ) := by
    first
      | (rw [hyq]; push_cast; done)
      | (rw [hyq]; push_cast; ring)
  have hlt : (x:ℝ) < ((yq:ℚ):ℝ) := by
    rw [hyR]
    have hpos : 0 < 1 / (N:ℝ) := one_div_pos.mpr hNR
    linarith
  have hsub : ((yq:ℚ):ℝ) - (x:ℝ) = 1 / (N:ℝ) := by
    rw [hyR]
    ring
  have hfc : ContinuousOn f (Set.Icc (x:ℝ) ((yq:ℚ):ℝ)) := hC.continuous.continuousOn
  have hfd : DifferentiableOn ℝ f (Set.Ioo (x:ℝ) ((yq:ℚ):ℝ)) :=
    (hC.differentiable (by norm_num)).differentiableOn
  obtain ⟨c, hc, hdc⟩ := exists_deriv_eq_slope f hlt hfc hfd
  have hslope : deriv f c = ((r:ℝ) - (p:ℝ)) * (N:ℝ) := by
    rw [hdc, hsub, ← hr, ← hp]
    first
      | (field_simp; done)
      | rw [div_eq_mul_inv, one_div, inv_inv]
      | (field_simp; ring)
  refine ⟨c, hc.1, ?_, kr - kp, ?_⟩
  · have := hc.2
    linarith
  · rw [hslope]
    push_cast
    linear_combination hrR - hpR

theorem pb5_const (g : ℝ → ℝ) (hg : Continuous g)
    (hint : ∀ y : ℝ, ∃ z : ℤ, g y = (z:ℝ)) (y : ℝ) : g y = g 0 := by
  obtain ⟨z, hz⟩ := hint y
  obtain ⟨c, hc⟩ := hint 0
  by_contra hne
  have hzc : z ≠ c := by
    intro h
    apply hne
    rw [hz, hc, h]
  have hsub : Set.uIcc (g 0) (g y) ⊆ g '' Set.uIcc 0 y :=
    intermediate_value_uIcc hg.continuousOn
  rcases lt_or_gt_of_ne hzc with hlt | hgt
  · have hz' : (z:ℝ) + 1 ≤ (c:ℝ) := by exact_mod_cast (show z + 1 ≤ c by omega)
    have hv : (c:ℝ) - 1/2 ∈ Set.uIcc (g 0) (g y) := by
      rw [hc, hz, Set.mem_uIcc]
      right
      constructor <;> linarith
    obtain ⟨t, _, ht⟩ := hsub hv
    obtain ⟨w, hw⟩ := hint t
    have h2 : ((2 * w : ℤ) : ℝ) = ((2 * c - 1 : ℤ) : ℝ) := by
      push_cast
      linarith
    have h3 : 2 * w = 2 * c - 1 := by exact_mod_cast h2
    omega
  · have hz' : (c:ℝ) + 1 ≤ (z:ℝ) := by exact_mod_cast (show c + 1 ≤ z by omega)
    have hv : (c:ℝ) + 1/2 ∈ Set.uIcc (g 0) (g y) := by
      rw [hc, hz, Set.mem_uIcc]
      left
      constructor <;> linarith
    obtain ⟨t, _, ht⟩ := hsub hv
    obtain ⟨w, hw⟩ := hint t
    have h2 : ((2 * w : ℤ) : ℝ) = ((2 * c + 1 : ℤ) : ℝ) := by
      push_cast
      linarith
    have h3 : 2 * w = 2 * c + 1 := by exact_mod_cast h2
    omega

theorem pb5_int_of_dense (g : ℝ → ℝ) (hg : Continuous g)
    (hden : ∀ y : ℝ, ∀ ε : ℝ, 0 < ε → ∃ ξ : ℝ, |ξ - y| < ε ∧ ∃ z : ℤ, g ξ = (z:ℝ))
    (y : ℝ) : ∃ z : ℤ, g y = (z:ℝ) := by
  by_contra hno
  have hn : ((⌊g y⌋ : ℤ) : ℝ) ≤ g y := Int.floor_le (g y)
  have hn2 : g y < ((⌊g y⌋ : ℤ) : ℝ) + 1 := Int.lt_floor_add_one (g y)
  have hne : ((⌊g y⌋ : ℤ) : ℝ) ≠ g y := fun h => hno ⟨⌊g y⌋, h.symm⟩
  have hlt : ((⌊g y⌋ : ℤ) : ℝ) < g y := lt_of_le_of_ne hn hne
  have hδ : 0 < min (g y - ((⌊g y⌋ : ℤ) : ℝ)) (((⌊g y⌋ : ℤ) : ℝ) + 1 - g y) :=
    lt_min (by linarith) (by linarith)
  obtain ⟨η, hη, hgη⟩ := Metric.continuous_iff.mp hg y _ hδ
  obtain ⟨ξ, hξ, z, hz⟩ := hden y η hη
  have hd : dist (g ξ) (g y)
      < min (g y - ((⌊g y⌋ : ℤ) : ℝ)) (((⌊g y⌋ : ℤ) : ℝ) + 1 - g y) :=
    hgη ξ (by rw [Real.dist_eq]; exact hξ)
  rw [Real.dist_eq, hz] at hd
  have hd1 := abs_lt.mp hd
  have hm1 := min_le_left (g y - ((⌊g y⌋ : ℤ) : ℝ)) (((⌊g y⌋ : ℤ) : ℝ) + 1 - g y)
  have hm2 := min_le_right (g y - ((⌊g y⌋ : ℤ) : ℝ)) (((⌊g y⌋ : ℤ) : ℝ) + 1 - g y)
  have hA : ((⌊g y⌋ : ℤ) : ℝ) < (z:ℝ) := by linarith [hd1.1]
  have hB : (z:ℝ) < ((⌊g y⌋ : ℤ) : ℝ) + 1 := by linarith [hd1.2]
  have hA' : ⌊g y⌋ < z := by exact_mod_cast hA
  have hB' : z < ⌊g y⌋ + 1 := by exact_mod_cast hB
  omega

theorem putnam_2008_b5 (fqsat : (ℝ → ℝ) → ℚ → Prop)
(hfqsat : ∀ f q, fqsat f q ↔ ContDiff ℝ 1 f ∧ (∃ p : ℚ, p = f q ∧ p.den = q.den))
: ∀ f : (ℝ → ℝ), (∀ q : ℚ, fqsat f q) ↔ f ∈ putnam_2008_b5_solution := by
  intro f
  constructor
  · intro hf
    have hval : ∀ q : ℚ, ∃ p : ℚ, (p:ℝ) = f (q:ℝ) ∧ p.den = q.den :=
      fun q => ((hfqsat f q).mp (hf q)).2
    have hC : ContDiff ℝ 1 f := ((hfqsat f 0).mp (hf 0)).1
    have hdiff : Differentiable ℝ f := hC.differentiable (by norm_num)
    have hgcont : Continuous (deriv f) := hC.continuous_deriv (by norm_num)
    have hden : ∀ y : ℝ, ∀ ε : ℝ, 0 < ε → ∃ ξ : ℝ, |ξ - y| < ε ∧ ∃ z : ℤ, deriv f ξ = (z:ℝ) := by
      intro y ε hε
      obtain ⟨x, hx1, hx2⟩ := exists_rat_btwn (show y - ε / 2 < y + ε / 2 by linarith)
      have h2ε : 0 < 2 / ε := div_pos (by norm_num) hε
      obtain ⟨m, hm⟩ := exists_nat_gt (2 / ε)
      have hmR0 : (0:ℝ) < (m:ℝ) := lt_trans h2ε hm
      have hmpos : 0 < m := Nat.cast_pos.mp hmR0
      have hεm : 2 < (m:ℝ) * ε := by
        rw [div_lt_iff₀ hε] at hm
        exact hm
      obtain ⟨N, hN⟩ : ∃ N : ℕ, N = x.den * m := ⟨_, rfl⟩
      have hdpos : 0 < x.den := by
        first
          | exact x.den_pos
          | exact x.pos
          | exact Rat.den_pos x
      have hmN' : m ≤ N := by
        rw [hN]
        first
          | exact Nat.le_mul_of_pos_left m hdpos
          | nlinarith
      have hmR : (m:ℝ) ≤ (N:ℝ) := by exact_mod_cast hmN'
      have hNR : (0:ℝ) < (N:ℝ) := lt_of_lt_of_le hmR0 hmR
      have hinv : 1 / (N:ℝ) < ε / 2 := by
        rw [div_lt_iff₀ hNR]
        nlinarith [mul_le_mul_of_nonneg_left hmR hε.le]
      obtain ⟨ξ, hξ1, hξ2, z, hz⟩ := pb5_key f hC hval x m N hmpos hN
      refine ⟨ξ, ?_, z, hz⟩
      rw [abs_lt]
      constructor <;> linarith
    have hint : ∀ y : ℝ, ∃ z : ℤ, deriv f y = (z:ℝ) :=
      fun y => pb5_int_of_dense (deriv f) hgcont hden y
    have hconst : ∀ y : ℝ, deriv f y = deriv f 0 :=
      fun y => pb5_const (deriv f) hgcont hint y
    obtain ⟨c, hc⟩ := hint 0
    have hdc : ∀ x : ℝ, deriv f x = (c:ℝ) := fun x => (hconst x).trans hc
    have hderiv0 : ∀ x : ℝ, deriv (fun t => f t - (c:ℝ) * t) x = 0 := by
      intro x
      have h1 : HasDerivAt (fun t => f t - (c:ℝ) * t) (deriv f x - (c:ℝ) * 1) x :=
        ((hdiff x).hasDerivAt).sub ((hasDerivAt_id x).const_mul (c:ℝ))
      rw [h1.deriv, hdc x]
      ring
    have hG : Differentiable ℝ (fun t => f t - (c:ℝ) * t) := by
      first
        | fun_prop
        | exact hdiff.sub (differentiable_id.const_mul (c:ℝ))
    have hF : ∀ x : ℝ, f x = (c:ℝ) * x + f 0 := by
      intro x
      have h : (fun t => f t - (c:ℝ) * t) x = (fun t => f t - (c:ℝ) * t) 0 := by
        first
          | exact is_const_of_deriv_eq_zero hG hderiv0 x 0
          | exact isConst_of_deriv_eq_zero hG hderiv0 x 0
      simp only [mul_zero, sub_zero] at h
      linarith
    obtain ⟨d, hd⟩ : ∃ d : ℤ, f 0 = (d:ℝ) := by
      obtain ⟨p0, hp0, hp0d⟩ := hval 0
      have hd1 : p0.den = 1 := by simpa using hp0d
      have hnum : (p0:ℚ) * (p0.den:ℚ) = (p0.num:ℚ) := pb5_mul_den p0
      rw [hd1] at hnum
      have hpq : (p0:ℚ) = ((p0.num:ℤ):ℚ) := by simpa using hnum
      have hf0 : f 0 = (p0:ℝ) := by simp [hp0]
      refine ⟨p0.num, ?_⟩
      rw [hf0]
      calc (p0:ℝ) = ((((p0.num:ℤ)):ℚ):ℝ) := by rw [← hpq]
        _ = ((p0.num:ℤ):ℝ) := by simp
    have hfx : ∀ x : ℝ, f x = (c:ℝ) * x + (d:ℝ) := by
      intro x
      rw [hF x, hd]
    have hcond : ∀ m : ℕ, 0 < m → (m:ℤ) ∣ c → m = 1 := by
      intro m hm hdiv
      obtain ⟨k, hk⟩ := hdiv
      have hm0 : m ≠ 0 := by omega
      have hm0R : (m:ℝ) ≠ 0 := by exact_mod_cast hm0
      have hkR : (c:ℝ) = (m:ℝ) * (k:ℝ) := by exact_mod_cast hk
      have hcancel : (m:ℝ) * (k:ℝ) * (m:ℝ)⁻¹ = (k:ℝ) := by
        rw [mul_comm (m:ℝ), mul_assoc, mul_inv_cancel₀ hm0R, mul_one]
      have hq : (((m:ℚ)⁻¹ : ℚ) : ℝ) = (m:ℝ)⁻¹ := by
        first
          | (simp; done)
          | (push_cast; rfl)
          | norm_num
      have hfq : f (((m:ℚ)⁻¹ : ℚ) : ℝ) = (k:ℝ) + (d:ℝ) := by
        rw [hfx, hq, hkR, hcancel]
      obtain ⟨p, hp, hpd⟩ := hval ((m:ℚ)⁻¹)
      have hpk : (p:ℝ) = ((k + d : ℤ) : ℝ) := by
        rw [hp, hfq]
        norm_num
      have hpZ : p = ((k + d : ℤ) : ℚ) := by exact_mod_cast hpk
      have hD : ((m:ℚ)⁻¹).den = m := by
        first
          | rw [Rat.inv_natCast_den, if_neg hm0]
          | simp [Rat.inv_natCast_den, hm0]
      have h1 : ((k + d : ℤ) : ℚ).den = 1 := by
        first
          | exact Rat.den_intCast _
          | exact Rat.intCast_den _
          | simp
      rw [hpZ, h1, hD] at hpd
      omega
    have hc1 : c = 1 ∨ c = -1 := by
      by_cases hc0 : c = 0
      · exfalso
        have h2 := hcond 2 (by norm_num) (by rw [hc0]; simp)
        omega
      · have hnat : c.natAbs = 1 := by
          apply hcond c.natAbs (Int.natAbs_pos.mpr hc0)
          first
            | exact Int.natAbs_dvd.mpr dvd_rfl
            | simp
        rcases Int.natAbs_eq c with h | h <;> rw [hnat] at h <;> omega
    simp only [putnam_2008_b5_solution, Set.mem_union, Set.mem_setOf_eq]
    rcases hc1 with rfl | rfl
    · left
      refine ⟨d, ?_⟩
      funext x
      rw [hfx x]
      norm_num
    · right
      refine ⟨d, ?_⟩
      funext x
      rw [hfx x]
      norm_num
  · intro hmem
    simp only [putnam_2008_b5_solution, Set.mem_union, Set.mem_setOf_eq] at hmem
    rcases hmem with ⟨n, rfl⟩ | ⟨n, rfl⟩
    · intro q
      rw [hfqsat]
      refine ⟨(by first | fun_prop | exact contDiff_id.add contDiff_const), (q + (n:ℚ)), ?_, pb5_den_add_int q n⟩
      simp
    · intro q
      rw [hfqsat]
      refine ⟨(by first | fun_prop | exact contDiff_id.neg.add contDiff_const), (-q + (n:ℚ)), ?_, ?_⟩
      · simp
      · exact (pb5_den_add_int (-q) n).trans (by simp)

#print axioms putnam_2008_b5
