import Mathlib

open Finset

open Matrix Function Real

theorem putnam_2012_b1 (S : Set (Set.Ici (0 : ℝ) → ℝ))
(rngS : ∀ f ∈ S, ∀ x : Set.Ici (0 : ℝ), f x ∈ Set.Ici (0 : ℝ))
(f1 : Set.Ici (0 : ℝ) → ℝ)
(hf1 : f1 = fun (x : Set.Ici (0 : ℝ)) ↦ exp x - 1)
(f2 : Set.Ici (0 : ℝ) → ℝ)
(hf2 : f2 = fun (x : Set.Ici (0 : ℝ)) ↦ Real.log (x + 1))
(hf1mem : f1 ∈ S)
(hf2mem : f2 ∈ S)
(hsum : ∀ f ∈ S, ∀ g ∈ S, (fun x ↦ (f x) + (g x)) ∈ S)
(hcomp : ∀ f ∈ S, ∀ g ∈ S, ∀ gnneg : Set.Ici (0 : ℝ) → Set.Ici (0 : ℝ), ((∀ x : Set.Ici (0 : ℝ), g x = gnneg x) → (fun x ↦ f (gnneg x)) ∈ S))
(hdiff : ∀ f ∈ S, ∀ g ∈ S, (∀ x : Set.Ici (0 : ℝ), f x ≥ g x) → (fun x ↦ (f x) - (g x)) ∈ S)
: (∀ f ∈ S, ∀ g ∈ S, (fun x ↦ (f x) * (g x)) ∈ S) := by
  have nn : ∀ h ∈ S, ∀ x : Set.Ici (0:ℝ), 0 ≤ h x :=
    fun h hh x => Set.mem_Ici.mp (rngS h hh x)
  have hL : ∀ h ∈ S, (∀ x : Set.Ici (0:ℝ), 0 ≤ h x) →
      (fun x : Set.Ici (0:ℝ) => Real.log (h x + 1)) ∈ S := by
    intro h hh hn
    have h1 : (fun x : Set.Ici (0:ℝ) => f2 ⟨h x, hn x⟩) ∈ S :=
      hcomp f2 hf2mem h hh (fun x => ⟨h x, hn x⟩) (fun x => rfl)
    have e : (fun x : Set.Ici (0:ℝ) => f2 ⟨h x, hn x⟩)
        = (fun x : Set.Ici (0:ℝ) => Real.log (h x + 1)) := by
      funext x
      subst hf2
      rfl
    rw [← e]
    exact h1
  have hE : ∀ h ∈ S, (∀ x : Set.Ici (0:ℝ), 0 ≤ h x) →
      (fun x : Set.Ici (0:ℝ) => Real.exp (h x) - 1) ∈ S := by
    intro h hh hn
    have h1 : (fun x : Set.Ici (0:ℝ) => f1 ⟨h x, hn x⟩) ∈ S :=
      hcomp f1 hf1mem h hh (fun x => ⟨h x, hn x⟩) (fun x => rfl)
    have e : (fun x : Set.Ici (0:ℝ) => f1 ⟨h x, hn x⟩)
        = (fun x : Set.Ici (0:ℝ) => Real.exp (h x) - 1) := by
      funext x
      subst hf1
      rfl
    rw [← e]
    exact h1
  intro f hf g hg
  have hLf := hL f hf (nn f hf)
  have hLg := hL g hg (nn g hg)
  have hLsum : (fun x : Set.Ici (0:ℝ) => Real.log (f x + 1) + Real.log (g x + 1)) ∈ S :=
    hsum _ hLf _ hLg
  have nnsum : ∀ x : Set.Ici (0:ℝ), 0 ≤ Real.log (f x + 1) + Real.log (g x + 1) := by
    intro x
    have h1 : 0 ≤ Real.log (f x + 1) := Real.log_nonneg (by linarith [nn f hf x])
    have h2 : 0 ≤ Real.log (g x + 1) := Real.log_nonneg (by linarith [nn g hg x])
    linarith
  have hFmem : (fun x : Set.Ici (0:ℝ) => Real.exp (Real.log (f x + 1) + Real.log (g x + 1)) - 1) ∈ S :=
    hE (fun x : Set.Ici (0:ℝ) => Real.log (f x + 1) + Real.log (g x + 1)) hLsum nnsum
  have hGmem : (fun x : Set.Ici (0:ℝ) => f x + g x) ∈ S := hsum f hf g hg
  have key : ∀ x : Set.Ici (0:ℝ),
      Real.exp (Real.log (f x + 1) + Real.log (g x + 1)) - 1 = f x + g x + f x * g x := by
    intro x
    have hfx : (0:ℝ) < f x + 1 := by linarith [nn f hf x]
    have hgx : (0:ℝ) < g x + 1 := by linarith [nn g hg x]
    rw [Real.exp_add, Real.exp_log hfx, Real.exp_log hgx]
    ring
  have hfinal : (fun x : Set.Ici (0:ℝ) =>
      (Real.exp (Real.log (f x + 1) + Real.log (g x + 1)) - 1) - (f x + g x)) ∈ S :=
    hdiff (fun x : Set.Ici (0:ℝ) => Real.exp (Real.log (f x + 1) + Real.log (g x + 1)) - 1)
      hFmem (fun x : Set.Ici (0:ℝ) => f x + g x) hGmem
      (fun x => by
        show Real.exp (Real.log (f x + 1) + Real.log (g x + 1)) - 1 ≥ f x + g x
        linarith [key x, mul_nonneg (nn f hf x) (nn g hg x)])
  have eq : (fun x : Set.Ici (0:ℝ) =>
      (Real.exp (Real.log (f x + 1) + Real.log (g x + 1)) - 1) - (f x + g x))
      = (fun x : Set.Ici (0:ℝ) => f x * g x) := by
    funext x
    show (Real.exp (Real.log (f x + 1) + Real.log (g x + 1)) - 1) - (f x + g x) = f x * g x
    linarith [key x]
  rw [eq] at hfinal
  exact hfinal

#print axioms putnam_2012_b1
