import Mathlib

open Finset

theorem s_prime_sq_24 (p : ℕ) (hp : p.Prime) (h5 : 5 ≤ p) : 24 ∣ p ^ 2 - 1 := by
  have hd2 : ¬ 2 ∣ p := fun h => by
    have := hp.eq_one_or_self_of_dvd 2 h
    omega
  have hd3 : ¬ 3 ∣ p := fun h => by
    have := hp.eq_one_or_self_of_dvd 3 h
    omega
  have key : p ^ 2 % 24 = 1 := by
    rw [Nat.pow_mod]
    have hlt : p % 24 < 24 := Nat.mod_lt _ (by norm_num)
    interval_cases hr : p % 24 <;> first | omega | norm_num
  generalize p ^ 2 = n at key ⊢
  omega

#print axioms s_prime_sq_24
