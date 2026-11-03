import Mathlib

open Finset

open Topology Filter Nat
abbrev putnam_2014_b1_solution : Set ℕ := {n : ℕ | n > 0 ∧ ¬∃ a ∈ digits 10 n, a = 0}

theorem pb1_ofd (a : ℕ) (t : List ℕ) :
    Nat.ofDigits 10 (a :: t) = a + 10 * Nat.ofDigits 10 t := by
  first
  | rfl
  | simp [Nat.ofDigits]
  | simp [Nat.ofDigits_cons]

theorem pb1_single (a : ℕ) : [a].getLastI = a := by
  first
  | rfl
  | simp [List.getLastI]

theorem pb1_gl_aux : ∀ (n : ℕ) (s : List ℕ), s.length ≤ n → ∀ a b : ℕ,
    (a :: b :: s).getLastI = (b :: s).getLastI := by
  intro n
  induction n with
  | zero =>
    intro s hs a b
    have hs0 : s = [] := by
      rcases s with _ | ⟨c, s'⟩
      · rfl
      · exact absurd hs (by simp)
    subst hs0
    first
    | rfl
    | simp [List.getLastI]
  | succ n ih =>
    intro s hs a b
    rcases s with _ | ⟨c, _ | ⟨e, _ | ⟨f, _ | ⟨g, rest⟩⟩⟩⟩
    · first
      | rfl
      | simp [List.getLastI]
    · first
      | rfl
      | simp [List.getLastI]
    · first
      | rfl
      | simp [List.getLastI]
    · first
      | rfl
      | simp [List.getLastI]
    · have hl : rest.length ≤ n := by
        simp at hs
        omega
      exact ih rest hl f g

theorem pb1_gl_cons (a : ℕ) (t : List ℕ) (ht : t ≠ []) :
    (a :: t).getLastI = t.getLastI := by
  rcases t with _ | ⟨b, s⟩
  · exact absurd rfl ht
  · exact pb1_gl_aux s.length s le_rfl a b

theorem pb1_zero : ∀ (t : List ℕ), (∀ x ∈ t, x < 11) → Nat.ofDigits 10 t = 0 →
    t.getLastI = 0 := by
  intro t
  induction t with
  | nil =>
    intro _ _
    rfl
  | cons a s ih =>
    intro hb h0
    rw [pb1_ofd] at h0
    have ha : a = 0 := by omega
    have hs : Nat.ofDigits 10 s = 0 := by omega
    have hb' : ∀ x ∈ s, x < 11 := fun x hx => hb x (by simp [hx])
    rcases s with _ | ⟨c, s'⟩
    · rw [pb1_single]
      exact ha
    · rw [pb1_gl_cons a (c :: s') (by simp)]
      exact ih hb' hs

theorem pb1_sum : ∀ d : List ℕ,
    ∑ i : Fin d.length, d.get i * 10 ^ i.1 = Nat.ofDigits 10 d := by
  intro d
  induction d with
  | nil => simp [Nat.ofDigits]
  | cons a t ih =>
    rw [pb1_ofd]
    show ∑ i : Fin (t.length + 1), (a :: t).get i * 10 ^ i.1 = a + 10 * Nat.ofDigits 10 t
    rw [Fin.sum_univ_succ, ← ih, Finset.mul_sum]
    congr 1
    · simp
    · apply Finset.sum_congr rfl
      intro i _
      simp [pow_succ] <;> ring

theorem pb1_conv (N : ℕ) (d : List ℕ) :
    (N = ∑ i : Fin d.length, d.get i * 10 ^ i.1 ∧ d.getLastI ≠ 0 ∧ ∀ a ∈ d, a ∈ Finset.range 11)
      ↔ (N = Nat.ofDigits 10 d ∧ d.getLastI ≠ 0 ∧ ∀ x ∈ d, x < 11) := by
  rw [pb1_sum d]
  simp only [Finset.mem_range]

theorem pb1_zrec (N : ℕ) (hN : 0 < N) :
    (∃ a ∈ Nat.digits 10 N, a = 0) ↔
      (N % 10 = 0 ∨ ∃ a ∈ Nat.digits 10 (N / 10), a = 0) := by
  rw [Nat.digits_def' (by norm_num : (1:ℕ) < 10) hN]
  constructor
  · rintro ⟨a, ha, rfl⟩
    rcases List.mem_cons.1 ha with h | h
    · exact Or.inl h.symm
    · exact Or.inr ⟨0, h, rfl⟩
  · rintro (h | ⟨a, ha, rfl⟩)
    · exact ⟨N % 10, by simp, h⟩
    · exact ⟨0, by simp [ha], rfl⟩

theorem pb1_exist : ∀ N : ℕ, 0 < N →
    ∃ d : List ℕ, N = Nat.ofDigits 10 d ∧ d.getLastI ≠ 0 ∧ ∀ x ∈ d, x < 11 := by
  intro N
  induction N using Nat.strong_induction_on with
  | _ N ih =>
    intro hN
    by_cases hq : N / 10 = 0
    · refine ⟨[N], ?_, ?_, ?_⟩
      · simp [Nat.ofDigits]
      · rw [pb1_single]
        omega
      · intro x hx
        simp at hx
        omega
    · have hqpos : 0 < N / 10 := Nat.pos_of_ne_zero hq
      obtain ⟨e, he1, he2, he3⟩ := ih (N / 10) (by omega) hqpos
      have hne : e ≠ [] := by
        intro h
        subst h
        have h0 : Nat.ofDigits 10 ([] : List ℕ) = 0 := rfl
        omega
      refine ⟨N % 10 :: e, ?_, ?_, ?_⟩
      · rw [pb1_ofd]
        omega
      · rw [pb1_gl_cons (N % 10) e hne]
        exact he2
      · intro x hx
        rcases List.mem_cons.1 hx with h | h
        · omega
        · exact he3 x h

theorem pb1_uniq : ∀ N : ℕ, 0 < N → ¬ (∃ a ∈ Nat.digits 10 N, a = 0) →
    ∀ d e : List ℕ, (N = Nat.ofDigits 10 d ∧ d.getLastI ≠ 0 ∧ ∀ x ∈ d, x < 11) →
      (N = Nat.ofDigits 10 e ∧ e.getLastI ≠ 0 ∧ ∀ x ∈ e, x < 11) → d = e := by
  intro N
  induction N using Nat.strong_induction_on with
  | _ N ih =>
    intro hN hZ d e hd he
    have hF : N % 10 ≠ 0 := fun h => hZ ((pb1_zrec N hN).2 (Or.inl h))
    have hZ' : ¬ (∃ a ∈ Nat.digits 10 (N / 10), a = 0) :=
      fun h => hZ ((pb1_zrec N hN).2 (Or.inr h))
    obtain ⟨hd1, hd2, hd3⟩ := hd
    obtain ⟨he1, he2, he3⟩ := he
    have h0 : Nat.ofDigits 10 ([] : List ℕ) = 0 := rfl
    cases d with
    | nil =>
      exfalso
      omega
    | cons a t =>
      cases e with
      | nil =>
        exfalso
        omega
      | cons b s =>
        rw [pb1_ofd] at hd1 he1
        have ha11 : a < 11 := hd3 a (by simp)
        have hb11 : b < 11 := he3 b (by simp)
        have hab : a = b := by omega
        have hMs : Nat.ofDigits 10 t = Nat.ofDigits 10 s := by omega
        have ht3 : ∀ x ∈ t, x < 11 := fun x hx => hd3 x (by simp [hx])
        have hs3 : ∀ x ∈ s, x < 11 := fun x hx => he3 x (by simp [hx])
        by_cases hM : Nat.ofDigits 10 t = 0
        · have ht0 : t = [] := by
            by_contra hne
            rw [pb1_gl_cons a t hne] at hd2
            exact hd2 (pb1_zero t ht3 hM)
          have hs0 : s = [] := by
            by_contra hne
            rw [pb1_gl_cons b s hne] at he2
            exact he2 (pb1_zero s hs3 (by omega))
          rw [ht0, hs0, hab]
        · have hMpos : 0 < Nat.ofDigits 10 t := Nat.pos_of_ne_zero hM
          have hq : N / 10 = Nat.ofDigits 10 t := by omega
          have hlt : Nat.ofDigits 10 t < N := by omega
          have hZt : ¬ (∃ x ∈ Nat.digits 10 (Nat.ofDigits 10 t), x = 0) := by
            rw [← hq]
            exact hZ'
          have htne : t ≠ [] := by
            intro h
            subst h
            omega
          have hsne : s ≠ [] := by
            intro h
            subst h
            omega
          have hd2' : t.getLastI ≠ 0 := by
            rw [← pb1_gl_cons a t htne]
            exact hd2
          have he2' : s.getLastI ≠ 0 := by
            rw [← pb1_gl_cons b s hsne]
            exact he2
          have hres : t = s :=
            ih (Nat.ofDigits 10 t) hlt hMpos hZt t s ⟨rfl, hd2', ht3⟩ ⟨hMs, he2', hs3⟩
          rw [hab, hres]

theorem pb1_two : ∀ N : ℕ, 0 < N → (∃ a ∈ Nat.digits 10 N, a = 0) →
    ∃ d e : List ℕ, (N = Nat.ofDigits 10 d ∧ d.getLastI ≠ 0 ∧ ∀ x ∈ d, x < 11) ∧
      (N = Nat.ofDigits 10 e ∧ e.getLastI ≠ 0 ∧ ∀ x ∈ e, x < 11) ∧ d ≠ e := by
  intro N
  induction N using Nat.strong_induction_on with
  | _ N ih =>
    intro hN hZ
    have h0 : Nat.ofDigits 10 ([] : List ℕ) = 0 := rfl
    by_cases hr : N % 10 = 0
    · have hq : 0 < N / 10 := by omega
      obtain ⟨t, ht1, ht2, ht3⟩ := pb1_exist (N / 10) hq
      have htne : t ≠ [] := by
        intro h
        subst h
        omega
      have hd1 : N = Nat.ofDigits 10 (0 :: t) ∧ (0 :: t).getLastI ≠ 0 ∧
          ∀ x ∈ 0 :: t, x < 11 := by
        refine ⟨?_, ?_, ?_⟩
        · rw [pb1_ofd]
          omega
        · rw [pb1_gl_cons 0 t htne]
          exact ht2
        · intro x hx
          rcases List.mem_cons.1 hx with h | h
          · omega
          · exact ht3 x h
      by_cases hq2 : N / 10 = 1
      · refine ⟨0 :: t, [10], hd1, ⟨?_, ?_, ?_⟩, ?_⟩
        · rw [pb1_ofd]
          omega
        · rw [pb1_single]
          norm_num
        · intro x hx
          rw [List.mem_singleton] at hx
          omega
        · intro h
          injection h with h1 _
          omega
      · have hu : 0 < N / 10 - 1 := by omega
        obtain ⟨u, hu1, hu2, hu3⟩ := pb1_exist (N / 10 - 1) hu
        have hune : u ≠ [] := by
          intro h
          subst h
          omega
        refine ⟨0 :: t, 10 :: u, hd1, ⟨?_, ?_, ?_⟩, ?_⟩
        · rw [pb1_ofd]
          omega
        · rw [pb1_gl_cons 10 u hune]
          exact hu2
        · intro x hx
          rcases List.mem_cons.1 hx with h | h
          · omega
          · exact hu3 x h
        · intro h
          injection h with h1 _
          omega
    · have hZq : ∃ a ∈ Nat.digits 10 (N / 10), a = 0 := by
        rcases (pb1_zrec N hN).1 hZ with h | h
        · exact absurd h hr
        · exact h
      have hq0 : N / 10 ≠ 0 := by
        intro h
        rw [h] at hZq
        simp at hZq
      have hq : 0 < N / 10 := Nat.pos_of_ne_zero hq0
      obtain ⟨d, e, ⟨hd1, hd2, hd3⟩, ⟨he1, he2, he3⟩, hde⟩ :=
        ih (N / 10) (by omega) hq hZq
      have hdne : d ≠ [] := by
        intro h
        subst h
        omega
      have hene : e ≠ [] := by
        intro h
        subst h
        omega
      refine ⟨N % 10 :: d, N % 10 :: e, ⟨?_, ?_, ?_⟩, ⟨?_, ?_, ?_⟩, ?_⟩
      · rw [pb1_ofd]
        omega
      · rw [pb1_gl_cons (N % 10) d hdne]
        exact hd2
      · intro x hx
        rcases List.mem_cons.1 hx with h | h
        · omega
        · exact hd3 x h
      · rw [pb1_ofd]
        omega
      · rw [pb1_gl_cons (N % 10) e hene]
        exact he2
      · intro x hx
        rcases List.mem_cons.1 hx with h | h
        · omega
        · exact he3 x h
      · intro h
        injection h with _ h2
        exact hde h2

theorem pb1_key (N : ℕ) (hN : 0 < N) :
    (∃! d : List ℕ, N = Nat.ofDigits 10 d ∧ d.getLastI ≠ 0 ∧ ∀ x ∈ d, x < 11) ↔
      ¬ (∃ a ∈ Nat.digits 10 N, a = 0) := by
  constructor
  · rintro ⟨d, hd, hu⟩ hZ
    obtain ⟨d1, d2, h1, h2, hne⟩ := pb1_two N hN hZ
    exact hne ((hu d1 h1).trans (hu d2 h2).symm)
  · intro hZ
    obtain ⟨d, hd⟩ := pb1_exist N hN
    exact ⟨d, hd, fun e he => pb1_uniq N hN hZ e d he hd⟩

theorem putnam_2014_b1 (overexpansion : ℕ → List ℕ → Prop)
(hoverexpansion : overexpansion = fun N d ↦ N = ∑ i : Fin d.length, (d.get i) * 10 ^ i.1 ∧ d.getLastI ≠ 0 ∧ ∀ a ∈ d, a ∈ Finset.range 11)
(S : Set ℕ)
(hS : ∀ N : ℕ, N ∈ S ↔ N > 0 ∧ ∃! d : List ℕ, overexpansion N d)
: (S = putnam_2014_b1_solution) := by
  subst hoverexpansion
  ext N
  rw [hS N]
  simp only [putnam_2014_b1_solution, Set.mem_setOf_eq]
  constructor
  · rintro ⟨hN, d, hd, hu⟩
    refine ⟨hN, (pb1_key N hN).1 ⟨d, (pb1_conv N d).1 hd,
      fun e he => hu e ((pb1_conv N e).2 he)⟩⟩
  · rintro ⟨hN, hZ⟩
    refine ⟨hN, ?_⟩
    obtain ⟨d, hd⟩ := pb1_exist N hN
    exact ⟨d, (pb1_conv N d).2 hd, fun e he => pb1_uniq N hN hZ e d ((pb1_conv N e).1 he) hd⟩

#print axioms putnam_2014_b1
