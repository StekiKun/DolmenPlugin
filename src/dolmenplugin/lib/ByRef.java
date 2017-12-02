package dolmenplugin.lib;

import java.util.Objects;

/**
 * A convenience class representing a mutable
 * cell containing an object of type {@code A}.
 * It can be used to mimick out-parameters.
 * <p>
 * Access to the cell value is thread-safe.
 * 
 * @author Stéphane Lescuyer
 *
 * @param <A>
 */
public final class ByRef<A> {

	private A value;
	
	private ByRef(A value) {
		this.value = value;
	}
	
	/**
	 * @return the contents of the cell, can be {@code null}
	 */
	public synchronized A get() {
		return value;
	}
	
	/**
	 * Sets the contents of the cell to {@code value}
	 * @param value	can be {@code null}
	 */
	public synchronized void set(A value) {
		this.value = value;
	}
	
	/**
	 * @return a fresh cell with {@code null} contents
	 */
	public static <A> ByRef<A> make() {
		return new ByRef<A>(null);
	}
	
	/**
	 * @param a	can be {@code null}
	 * @return a fresh cell containing {@code a}
	 */
	public static <A> ByRef<A> of(A a) {
		return new ByRef<A>(a);
	}
	
	@Override
	public String toString() {
		return Objects.toString(value);
	}
	
}
