
theorem core_sq_mod_four (n : Nat) : n ^ 2 % 4 = 0 ∨ n ^ 2 % 4 = 1 := by
  have h : n % 4 = 0 ∨ n % 4 = 1 ∨ n % 4 = 2 ∨ n % 4 = 3 := by omega
  rcases h with h | h | h | h <;> rw [Nat.pow_mod, h] <;> decide

#print axioms core_sq_mod_four
