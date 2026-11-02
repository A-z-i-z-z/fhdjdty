
def sumTo : Nat → Nat
  | 0 => 0
  | n + 1 => (n + 1) + sumTo n

theorem core_sum_to (n : Nat) : 2 * sumTo n = n * (n + 1) := by
  induction n with
  | zero => rfl
  | succ k ih =>
    show 2 * ((k + 1) + sumTo k) = (k + 1) * (k + 1 + 1)
    have h1 : 2 * ((k + 1) + sumTo k) = 2 * (k + 1) + 2 * sumTo k := by omega
    rw [h1, ih]
    simp only [Nat.mul_add, Nat.add_mul, Nat.mul_one, Nat.one_mul]
    omega

#print axioms core_sum_to
