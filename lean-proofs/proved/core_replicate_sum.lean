
theorem core_replicate_sum (n k : Nat) : (List.replicate n k).sum = n * k := by
  induction n with
  | zero => simp
  | succ m ih =>
    simp only [List.replicate_succ, List.sum_cons, ih]
    rw [Nat.add_mul, Nat.one_mul, Nat.add_comm]

#print axioms core_replicate_sum
