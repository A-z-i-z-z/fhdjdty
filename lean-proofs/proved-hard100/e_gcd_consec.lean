import Mathlib

open Finset

theorem e_gcd_consec (n : ℕ) : Nat.gcd n (n + 1) = 1 := by
  have a : Nat.gcd n (n + 1) ∣ n := Nat.gcd_dvd_left n (n + 1)
  have b : Nat.gcd n (n + 1) ∣ n + 1 := Nat.gcd_dvd_right n (n + 1)
  have h1 : Nat.gcd n (n + 1) ∣ 1 := by
    first
    | simpa using Nat.dvd_sub b a
    | simpa using Nat.dvd_sub' b a
    | exact (Nat.dvd_add_right a).mp b
  exact Nat.dvd_one.mp h1

#print axioms e_gcd_consec
