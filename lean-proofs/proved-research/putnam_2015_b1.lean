import Mathlib

open Finset

theorem pb1_rolle_card (F : ℝ → ℝ) (hF : Differentiable ℝ F) (s : Finset ℝ) :
    (∀ x ∈ s, F x = 0) →
      ∃ t : Finset ℝ, (∀ y ∈ t, deriv F y = 0) ∧ (∀ y ∈ t, ∃ x ∈ s, y < x) ∧
        s.card ≤ t.card + 1 := by
  induction s using Finset.induction_on_max
  next =>
    intro _
    exact ⟨∅, by simp, by simp, by simp⟩
  next a s ha ih =>
    intro hs
    have hsa : ∀ x ∈ s, F x = 0 := fun x hx => hs x (Finset.mem_insert_of_mem hx)
    have hFa : F a = 0 := hs a (Finset.mem_insert_self a s)
    have has : a ∉ s := fun h => lt_irrefl a (ha a h)
    obtain ⟨t, ht0, htlt, htc⟩ := ih hsa
    rcases s.eq_empty_or_nonempty with hse | hne
    · subst hse
      refine ⟨∅, by simp, by simp, ?_⟩
      simp
    · obtain ⟨m, hm, hmax⟩ := Finset.exists_max_image s id hne
      have hma : m < a := ha m hm
      have hFm : F m = 0 := hsa m hm
      obtain ⟨c, hc, hdc⟩ :=
        exists_deriv_eq_zero hma hF.continuous.continuousOn (by rw [hFm, hFa])
      have hcm : m < c := hc.1
      have hca : c < a := hc.2
      have hct : c ∉ t := by
        intro hct
        obtain ⟨x, hx, hyx⟩ := htlt c hct
        have hxm : x ≤ m := hmax x hx
        linarith
      refine ⟨insert c t, ?_, ?_, ?_⟩
      · intro y hy
        rcases Finset.mem_insert.mp hy with h | h
        · rw [h]; exact hdc
        · exact ht0 y h
      · intro y hy
        rcases Finset.mem_insert.mp hy with h | h
        · exact ⟨a, Finset.mem_insert_self a s, by rw [h]; exact hca⟩
        · obtain ⟨x, hx, hyx⟩ := htlt y h
          exact ⟨x, Finset.mem_insert_of_mem hx, hyx⟩
      · have e1 : (insert a s).card = s.card + 1 := by
          first
          | exact Finset.card_insert_of_notMem has
          | exact Finset.card_insert_of_not_mem has
        have e2 : (insert c t).card = t.card + 1 := by
          first
          | exact Finset.card_insert_of_notMem hct
          | exact Finset.card_insert_of_not_mem hct
        omega

theorem pb1_enc_finset (S : Set ℝ) (n : ℕ) (h : (n : ENat) ≤ S.encard) :
    ∃ s : Finset ℝ, (↑s : Set ℝ) ⊆ S ∧ n ≤ s.card := by
  obtain ⟨T, hTS, hT⟩ := Set.exists_subset_encard_eq h
  have hfin : T.Finite := by
    first
    | exact Set.finite_of_encard_eq_coe hT
    | exact Set.finite_of_encard_le_coe hT.le
  refine ⟨hfin.toFinset, ?_, ?_⟩
  · rw [Set.Finite.coe_toFinset]
    exact hTS
  · have e := hfin.encard_eq_coe_toFinset_card
    rw [hT] at e
    have e2 : n = hfin.toFinset.card := by exact_mod_cast e
    omega

theorem pb1_deriv_expmul (h : ℝ → ℝ) (hh : Differentiable ℝ h) :
    deriv (fun y => Real.exp (y / 2) * h y) = fun x => Real.exp (x / 2) * (deriv h x + h x / 2) := by
  funext x
  have h0 : HasDerivAt (fun y : ℝ => y / 2) (1 / 2) x := (hasDerivAt_id x).div_const 2
  have h1 : HasDerivAt (fun y : ℝ => Real.exp (y / 2)) (Real.exp (x / 2) * (1 / 2)) x := h0.exp
  have h2 := h1.mul (hh x).hasDerivAt
  refine h2.deriv.trans ?_
  first
  | ring
  | (beta_reduce; ring)

theorem putnam_2015_b1 (f : ℝ → ℝ)
(nzeros : (ℝ → ℝ) → ℕ → Prop)
(fdiff : ContDiff ℝ 2 f ∧ Differentiable ℝ (iteratedDeriv 2 f))
(hnzeros : ∀ f' : ℝ → ℝ, ∀ n : ℕ, nzeros f' n = ({x : ℝ | f' x = 0}.encard ≥ n))
(fzeros : nzeros f 5)
: nzeros (f + 6 * deriv f + 12 * iteratedDeriv 2 f + 8 * iteratedDeriv 3 f) 2 := by
  have hC : ContDiff ℝ 2 f := fdiff.1
  have hfd : Differentiable ℝ f := hC.differentiable (by norm_num)
  have hAd : Differentiable ℝ (deriv f) := by
    have h := hC.differentiable_iteratedDeriv 1 (by norm_num)
    rw [iteratedDeriv_one] at h
    exact h
  have h2 : iteratedDeriv 2 f = deriv (deriv f) := by
    first
    | rfl
    | rw [iteratedDeriv_succ, iteratedDeriv_one]
  have h3 : iteratedDeriv 3 f = deriv (deriv (deriv f)) := by
    first
    | rfl
    | rw [iteratedDeriv_succ, h2]
  have hBd : Differentiable ℝ (deriv (deriv f)) := by
    rw [← h2]
    exact fdiff.2
  have hexpAt : ∀ x : ℝ, HasDerivAt (fun y : ℝ => Real.exp (y / 2)) (Real.exp (x / 2) * (1 / 2)) x :=
    fun x => ((hasDerivAt_id x).div_const 2).exp
  have hF0 : Differentiable ℝ (fun y => Real.exp (y / 2) * f y) :=
    fun x => ((hexpAt x).mul (hfd x).hasDerivAt).differentiableAt
  have D1 : deriv (fun y => Real.exp (y / 2) * f y)
      = fun x => Real.exp (x / 2) * (deriv f x + f x / 2) :=
    pb1_deriv_expmul f hfd
  have hD1 : Differentiable ℝ (deriv (fun y => Real.exp (y / 2) * f y)) := by
    rw [D1]
    intro x
    exact ((hexpAt x).mul ((hAd x).hasDerivAt.add ((hfd x).hasDerivAt.div_const 2))).differentiableAt
  have hu1 : Differentiable ℝ (fun x => deriv f x + f x / 2) :=
    fun x => ((hAd x).hasDerivAt.add ((hfd x).hasDerivAt.div_const 2)).differentiableAt
  have hu1d : ∀ x, deriv (fun y => deriv f y + f y / 2) x = deriv (deriv f) x + deriv f x / 2 := by
    intro x
    exact ((hAd x).hasDerivAt.add ((hfd x).hasDerivAt.div_const 2)).deriv
  have D2 : deriv (deriv (fun y => Real.exp (y / 2) * f y))
      = fun x => Real.exp (x / 2) * ((deriv (deriv f) x + deriv f x / 2) + (deriv f x + f x / 2) / 2) := by
    rw [D1]
    refine (pb1_deriv_expmul (fun y => deriv f y + f y / 2) hu1).trans ?_
    funext x
    simp only [hu1d]
    try ring
  have hbAt : ∀ x : ℝ, HasDerivAt
      (fun y : ℝ => (deriv (deriv f) y + deriv f y / 2) + (deriv f y + f y / 2) / 2)
      (deriv (deriv (deriv f)) x + deriv (deriv f) x / 2 + (deriv (deriv f) x + deriv f x / 2) / 2) x :=
    fun x => ((hBd x).hasDerivAt.add ((hAd x).hasDerivAt.div_const 2)).add
      (((hAd x).hasDerivAt.add ((hfd x).hasDerivAt.div_const 2)).div_const 2)
  have hDD : Differentiable ℝ (deriv (deriv (fun y => Real.exp (y / 2) * f y))) := by
    rw [D2]
    intro x
    exact ((hexpAt x).mul (hbAt x)).differentiableAt
  have D3 : ∀ x, deriv (deriv (deriv (fun y => Real.exp (y / 2) * f y))) x
      = Real.exp (x / 2) * (deriv (deriv (deriv f)) x + 3 / 2 * deriv (deriv f) x
          + 3 / 4 * deriv f x + f x / 8) := by
    intro x
    rw [D2]
    refine ((hexpAt x).mul (hbAt x)).deriv.trans ?_
    ring
  have hGx : ∀ x, (f + 6 * deriv f + 12 * iteratedDeriv 2 f + 8 * iteratedDeriv 3 f) x
      = f x + 6 * deriv f x + 12 * deriv (deriv f) x + 8 * deriv (deriv (deriv f)) x := by
    intro x
    rw [h2, h3]
    simp [Pi.add_apply, Pi.mul_apply]
  rw [hnzeros] at fzeros ⊢
  obtain ⟨s, hsZ, hs5⟩ := pb1_enc_finset _ 5 fzeros
  have hs0 : ∀ x ∈ s, f x = 0 := fun x hx => hsZ (Finset.mem_coe.mpr hx)
  have hFs : ∀ x ∈ s, (fun y => Real.exp (y / 2) * f y) x = 0 := by
    intro x hx
    show Real.exp (x / 2) * f x = 0
    rw [hs0 x hx, mul_zero]
  obtain ⟨t1, ht1z, _, ht1c⟩ := pb1_rolle_card _ hF0 s hFs
  obtain ⟨t2, ht2z, _, ht2c⟩ := pb1_rolle_card _ hD1 t1 ht1z
  obtain ⟨t3, ht3z, _, ht3c⟩ := pb1_rolle_card _ hDD t2 ht2z
  have hsub : (↑t3 : Set ℝ) ⊆
      {x : ℝ | (f + 6 * deriv f + 12 * iteratedDeriv 2 f + 8 * iteratedDeriv 3 f) x = 0} := by
    intro y hy
    have hy' := ht3z y (Finset.mem_coe.mp hy)
    rw [D3 y] at hy'
    have hz := (mul_eq_zero.mp hy').resolve_left (Real.exp_pos (y / 2)).ne'
    show (f + 6 * deriv f + 12 * iteratedDeriv 2 f + 8 * iteratedDeriv 3 f) y = 0
    rw [hGx y]
    linarith
  have hcard : 2 ≤ t3.card := by omega
  have h1 : ((2 : ℕ) : ENat) ≤ (↑t3 : Set ℝ).encard := by
    rw [Set.encard_coe_eq_coe_finsetCard]
    exact_mod_cast hcard
  first
  | exact le_trans h1 (Set.encard_le_encard hsub)
  | exact le_trans h1 (Set.encard_mono hsub)

#print axioms putnam_2015_b1
