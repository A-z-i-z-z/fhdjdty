import Mathlib

open Finset

theorem o_hermite2 (x : ℝ) : ⌊x⌋ + ⌊x + 1 / 2⌋ = ⌊2 * x⌋ := by
  have h1 : (⌊x⌋ : ℝ) ≤ x := Int.floor_le x
  have h2 : x < (⌊x⌋ : ℝ) + 1 := Int.lt_floor_add_one x
  rcases lt_or_ge (x - ⌊x⌋) (1 / 2) with h | h
  · have hk : ⌊x + 1 / 2⌋ = ⌊x⌋ := by
      rw [Int.floor_eq_iff]
      constructor <;> push_cast <;> linarith
    have hn : ⌊2 * x⌋ = 2 * ⌊x⌋ := by
      rw [Int.floor_eq_iff]
      constructor <;> push_cast <;> linarith
    rw [hk, hn]
    ring
  · have hk : ⌊x + 1 / 2⌋ = ⌊x⌋ + 1 := by
      rw [Int.floor_eq_iff]
      constructor <;> push_cast <;> linarith
    have hn : ⌊2 * x⌋ = 2 * ⌊x⌋ + 1 := by
      rw [Int.floor_eq_iff]
      constructor <;> push_cast <;> linarith
    rw [hk, hn]
    ring

#print axioms o_hermite2
