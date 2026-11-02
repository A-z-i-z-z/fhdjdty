
theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by
  apply Nat.dvd_of_mod_eq_zero
  have h : n % 3 = 0 ∨ n % 3 = 1 ∨ n % 3 = 2 := by omega
  rcases h with h | h | h <;> simp [Nat.add_mod, Nat.mul_mod, Nat.pow_mod, h]

#print axioms core_three_dvd
