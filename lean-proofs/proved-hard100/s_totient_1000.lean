import Mathlib

open Finset

theorem s_totient_1000 : Nat.totient 1000 = 400 := by
  have h2 : Nat.totient (2^3) = 4 :=
    (Nat.totient_prime_pow (p := 2) (n := 3) (by norm_num) (by norm_num)).trans (by norm_num)
  have h5 : Nat.totient (5^3) = 100 :=
    (Nat.totient_prime_pow (p := 5) (n := 3) (by norm_num) (by norm_num)).trans (by norm_num)
  have hc : Nat.Coprime (2^3) (5^3) := by norm_num
  calc Nat.totient 1000 = Nat.totient (2^3 * 5^3) := by norm_num
    _ = Nat.totient (2^3) * Nat.totient (5^3) := Nat.totient_mul hc
    _ = 400 := by rw [h2, h5]

#print axioms s_totient_1000
