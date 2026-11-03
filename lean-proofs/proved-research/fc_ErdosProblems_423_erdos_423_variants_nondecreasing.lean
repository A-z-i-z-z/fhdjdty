import Mathlib

open Finset

section

open Finset BigOperators Filter Asymptotics

namespace Erdos423

def IsConsecutiveBlockSum (a : ℕ → ℕ) (k : ℕ) (m : ℕ) : Prop :=
  ∃ i j : ℕ, 1 ≤ i ∧ i + 1 ≤ j ∧ j + 1 ≤ k ∧
    m = ∑ l ∈ Finset.Icc i j, a l

def IsHofstadterSeq (a : ℕ → ℕ) : Prop :=
  a 1 = 1 ∧ a 2 = 2 ∧
  ∀ k : ℕ, 3 ≤ k →
    IsConsecutiveBlockSum a k (a k) ∧
    a (k - 1) < a k ∧
    ∀ m : ℕ, a (k - 1) < m → m < a k → ¬IsConsecutiveBlockSum a k m

theorem erdos_423.variants.nondecreasing :
    ∀ a : ℕ → ℕ, IsHofstadterSeq a →
    ∀ n m : ℕ, 1 ≤ n → n ≤ m → a n - n ≤ a m - m := by
  intro a ha n m hn hnm
  unfold IsHofstadterSeq at ha
  obtain ⟨h1, h2, h3⟩ := ha
  have step : ∀ k : ℕ, 1 ≤ k → a k < a (k + 1) := by
    intro k hk
    rcases Nat.lt_or_ge k 2 with hk2 | hk2
    · obtain rfl : k = 1 := by omega
      show a 1 < a 2
      omega
    · have h := h3 (k + 1) (by omega)
      obtain ⟨_, hlt, _⟩ := h
      have e : k + 1 - 1 = k := by omega
      rw [e] at hlt
      exact hlt
  have grow : ∀ p d : ℕ, 1 ≤ p → a p + d ≤ a (p + d) := by
    intro p d hp
    induction d with
    | zero => simp
    | succ d ih =>
      have hs := step (p + d) (by omega)
      have e : p + (d + 1) = p + d + 1 := by omega
      rw [e]
      omega
  have lower : n ≤ a n := by
    have g := grow 1 (n - 1) (le_refl 1)
    have e : 1 + (n - 1) = n := by omega
    rw [e, h1] at g
    omega
  obtain ⟨d, rfl⟩ := Nat.exists_eq_add_of_le hnm
  have g := grow n d hn
  omega

#print axioms erdos_423.variants.nondecreasing
