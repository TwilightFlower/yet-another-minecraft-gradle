package io.github.nuclearfarts.mcgradle.util;

import java.util.Objects;

public class Pair<L, R> {
	public final L left;
	public final R right;
	
	private final int hashCode;
	
	public Pair(L left, R right) {
		this.left = left;
		this.right = right;
		hashCode = Objects.hash(left, right);
	}
	
	@Override
	public boolean equals(Object other) {
		if(!(other instanceof Pair)) return false;
		Pair<?, ?> o = (Pair<?, ?>) other;
		return Objects.equals(left, o.left) && Objects.equals(right, o.right);
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
}
