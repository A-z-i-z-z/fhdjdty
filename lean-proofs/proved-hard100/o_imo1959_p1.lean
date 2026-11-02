import Mathlib

open Finset

theorem o_imo1959_p1 (n : ℕ) : Nat.gcd (21 * n + 4) (14 * n + 3) = 1 := by
  have h1 : Nat.gcd (21 * n + 4) (14 * n + 3) ∣ 1 := by
    have ha : Nat.gcd (21 * n + 4) (14 * n + 3) ∣ 21 * n + 4 := Nat.gcd_dvd_left _ _
    have hb : Nat.gcd (21 * n + 4) (14 * n + 3) ∣ 14 * n + 3 := Nat.gcd_dvd_right _ _
    have hc : Nat.gcd (21 * n + 4) (14 * n + 3) ∣ 3 * (14 * n + 3) := Dvd.dvd.mul_left hb 3
    have hd : Nat.gcd (21 * n + 4) (14 * n + 3) ∣ 2 * (21 * n + 4) := Dvd.dvd.mul_left ha 2
    have he : 3 * (14 * n + 3) = 2 * (21 * n + 4) + 1 := by ring
    rw [he] at hc
    exact (Nat.dvd_add_right hd).mp hc
  exact Nat.dvd_one.mp h1

#print axioms o_imo1959_p1
