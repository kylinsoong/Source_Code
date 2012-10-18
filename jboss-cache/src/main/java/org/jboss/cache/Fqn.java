package org.jboss.cache;


import net.jcip.annotations.Immutable;
import org.jboss.cache.annotations.Compat;
import org.jboss.cache.util.Util;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Arrays;
import java.util.List;

/**
 * A Fully Qualified Name (Fqn) is a list of names (typically Strings but can be any Object), which represent a path to
 * a particular {@link Node} or sometimes a {@link Region} in a {@link Cache}.
 * <p/>
 * This name can be absolute (i.e., relative from the root node - {@link #ROOT}), or relative to any node in the cache.
 * Reading the documentation on each API call that makes use of {@link org.jboss.cache.Fqn}s will tell you whether the
 * API expects a relative or absolute Fqn.
 * <p/>
 * For instance, using this class to fetch a particular node might look like this.  (Here data on "Joe" is kept under
 * the "Smith" surname node, under the "people" tree.)
 * <pre>
 * Fqn<String> abc = Fqn.fromString("/people/Smith/Joe/");
 * Node joesmith = Cache.getRoot().getChild(abc);
 * </pre>
 * Alternatively, the same Fqn could be constructed using a List<Object> or varargs:
 * <pre>
 * Fqn<String> abc = Fqn.fromElements("people", "Smith", "Joe");
 * </pre>
 * This is a bit more efficient to construct.
 * <p/>
 * Note that<br>
 * <p/>
 * <code>Fqn<String> f = Fqn.fromElements("/a/b/c");</code>
 * <p/>
 * is <b>not</b> the same as
 * <p/>
 * <code>Fqn<String> f = Fqn.fromString("/a/b/c");</code>
 * <p/>
 * The former will result in a single Fqn, called "/a/b/c" which hangs directly under Fqn.ROOT.
 * <p/>
 * The latter will result in 3 Fqns, called "a", "b" and "c", where "c" is a child of "b", "b" is a child of "a", and
 * "a" hangs off Fqn.ROOT.
 * <p/>
 * Another way to look at it is that the "/" separarator is only parsed when it forms part of a String passed in to
 * Fqn.fromString() and not otherwise.
 * <p/>
 * <B>Best practices</B>: Always creating Fqns - even when using some factory methods - can be expensive in the long
 * run, and as far as possible we recommend that client code holds on to their Fqn references and reuse them.  E.g.:
 * <code> // BAD!! for (int i=0; i<someBigNumber; i++) { cache.get(Fqn.fromString("/a/b/c"), "key" + i); } </code>
 * instead, do: <code> // Much better Fqn f = Fqn.fromString("/a/b/c"); for (int i=0; i<someBigNumber; i++) {
 * cache.get(f, "key" + i); } </code>
 *
 * @version $Revision: 8221 $
 */
@Immutable
@Compat(notes = "The generics, while originally intended to be removed in 3.0, have been retained for backward compat.")
public class Fqn<E> implements Comparable<Fqn<?>>, Externalizable {
   
   private static final long serialVersionUID = -6901735117605327068L;
   
   /**
    * Separator between FQN elements.
    */
   public static final String SEPARATOR = "/";

   protected Object[] elements;
   private transient int hash_code = 0;

   /**
    * Immutable root Fqn.
    */
   public static final Fqn ROOT = new Fqn();

   /**
    * A cached string representation of this Fqn, used by toString to it isn't calculated again every time.
    */
   protected String stringRepresentation;

   // ----------------- START: Private constructors for use by factory methods only. ----------------------

   /**
    * Public to satisfy Externalization.  // TODO: Remove this ctor as well as Externalization!!
    */
   public Fqn() {
      elements = new Object[]{};
   }

   // --- deprecated compat stuff

   /**
    * Constructs a FQN from a list of names.
    *
    * @param names List of names
    * @deprecated use {@link #fromList(java.util.List)} instead.  This constructor will be removed in 3.0.0.
    */
   @Deprecated
   @Compat
   public Fqn(List<? extends E> names) {
      // the list is unsafe - may be referenced externally
      this(names.toArray(), true);
   }

   /**
    * Constructs a Fqn from an array of names.
    *
    * @param names Names that comprose this Fqn
    * @deprecated use {@link #fromElements(Object[])} instead.  This constructor will be removed in 3.0.0.
    */
   @Deprecated
   @Compat
   public Fqn(E... names) {
      // safe - the list is created here.
      this(names, true);
   }

   /**
    * Constructs a Fqn from a base and relative Fqn.
    *
    * @param base     parent Fqn
    * @param relative Sub-Fqn relative to the parent
    * @deprecated use {@link #fromRelativeFqn(Fqn, Fqn)} instead.  This constructor will be removed in 3.0.0.
    */
   @Deprecated
   @Compat
   @SuppressWarnings("unchecked")
   public Fqn(Fqn<? extends E> base, Fqn<? extends E> relative) {
      this(base, (E[]) relative.elements);
   }

   /**
    * Constructs a Fqn from a base and two relative names.
    *
    * @param base       parent Fqn
    * @param childNames elements that denote the path to the Fqn, under the parent
    * @deprecated use {@link #fromRelativeElements(Fqn, Object[])} instead.  This constructor will be removed in 3.0.0.
    */
   @Deprecated
   @Compat
   public Fqn(Fqn<? extends E> base, E... childNames) {
      elements = new Object[base.elements.length + childNames.length];
      System.arraycopy(base.elements, 0, elements, 0, base.elements.length);
      System.arraycopy(childNames, 0, elements, base.elements.length, childNames.length);
   }

   // --- end deprecated stuff

   /**
    * If safe is false, Collections.unmodifiableList() is used to wrap the list passed in.  This is an optimisation so
    * Fqn.fromString(), probably the most frequently used factory method, doesn't end up needing to use the
    * unmodifiableList() since it creates the list internally.
    *
    * @param names List of names
    * @param safe  whether this list is referenced externally (safe = false) or not (safe = true).
    * @deprecated use {@link #fromList(java.util.List)} instead.  The boolean "safety" hint is calculated internally.
    *             This constructor will be removed in 3.0.0.
    */
   @Deprecated
   @Compat(notes = "Not truly deprecated, this constructor should really be protected and not public.  Marked as deprecated for anyone using it as a public API.")
   @SuppressWarnings("unchecked")
   protected Fqn(Object[] names, boolean safe) {
      if (names != null) {
         // if not safe make a defensive copy
         if (safe)
            elements = names;
         else {
            elements = new Object[names.length];
            System.arraycopy(names, 0, elements, 0, names.length);
         }
      } else {
         elements = new Object[]{};
      }
   }

   // ----------------- END: Private constructors for use by factory methods only. ----------------------

   /**
    * Retrieves an Fqn that represents the list of elements passed in.
    *
    * @param names list of elements that comprise the Fqn
    * @return an Fqn
    * @since 2.2.0
    */
   @SuppressWarnings("unchecked")
   public static <T> Fqn<T> fromList(List<? extends T> names) {
      return new Fqn<T>(names.toArray(), true);
   }

   /**
    * Retrieves an Fqn that represents the list of elements passed in.
    *
    * @param names list of elements that comprise the Fqn
    * @param safe  if true, the list passed in is not defensively copied but used directly.  <b>Use with care.</b>  Make
    *              sure you know what you are doing before you pass in a <tt>true</tt> value to <tt>safe</tt>, as it can
    *              have adverse effects on performance or correctness.  The defensive copy of list elements is not just
    *              for safety but also for performance as an appropriare List implementation is used, which works well
    *              with Fqn operations.
    * @return an Fqn
    */
   @SuppressWarnings("unchecked")
   @Compat
   public static <T> Fqn<T> fromList(List<? extends T> names, boolean safe) {
      return new Fqn<T>(names.toArray(), true); // this will always be safe!!
   }

   /**
    * Retrieves an Fqn that represents the array of elements passed in.
    *
    * @param elements array of elements that comprise the Fqn
    * @return an Fqn
    * @since 2.2.0
    */
   public static <T> Fqn<T> fromElements(T... elements) {
      return new Fqn<T>(elements, true);
   }

   /**
    * Retrieves an Fqn that represents the absolute Fqn of the relative Fqn passed in.
    *
    * @param base     base Fqn
    * @param relative relative Fqn
    * @return an Fqn
    * @since 2.2.0
    */
   @SuppressWarnings("unchecked")
   public static <T> Fqn<T> fromRelativeFqn(Fqn<? extends T> base, Fqn<? extends T> relative) {
      return new Fqn<T>(base, (T[]) relative.elements);
   }

   /**
    * Retrieves an Fqn that represents the List<Object> of elements passed in, relative to the base Fqn.
    *
    * @param base             base Fqn
    * @param relativeElements relative List<Object> of elements
    * @return an Fqn
    * @since 2.2.0
    */
   @SuppressWarnings("unchecked")
   public static <T> Fqn<T> fromRelativeList(Fqn<? extends T> base, List<? extends T> relativeElements) {
      return new Fqn<T>(base, (T[]) relativeElements.toArray());
   }

   /**
    * Retrieves an Fqn that represents the array of elements passed in, relative to the base Fqn.
    *
    * @param base             base Fqn
    * @param relativeElements relative elements
    * @return an Fqn
    * @since 2.2.0
    */
   public static <T> Fqn<T> fromRelativeElements(Fqn<? extends T> base, T... relativeElements) {
      return new Fqn<T>(base, relativeElements);
   }

   /**
    * Returns a new Fqn from a string, where the elements are deliminated by one or more separator ({@link #SEPARATOR})
    * characters.<br><br> Example use:<br>
    * <pre>
    * Fqn.fromString("/a/b/c/");
    * </pre><br>
    * is equivalent to:<br>
    * <pre>
    * Fqn.fromElements("a", "b", "c");
    * </pre>
    *
    * @param stringRepresentation String representation of the Fqn
    * @return an Fqn<String> constructed from the string representation passed in
    */
   @SuppressWarnings("unchecked")
   public static Fqn<String> fromString(String stringRepresentation) {
      if (stringRepresentation == null || stringRepresentation.equals(SEPARATOR) || stringRepresentation.equals(""))
         return root();

      String toMatch = stringRepresentation.startsWith(SEPARATOR) ? stringRepresentation.substring(1) : stringRepresentation;
      Object[] el = toMatch.split(SEPARATOR);
      return new Fqn(el, true);
   }

   /**
    * Retrieves an Fqn read from an object input stream, typically written to using {@link
    * #writeExternal(java.io.ObjectOutput)}.
    *
    * @param in input stream
    * @return an Fqn
    * @throws IOException            in the event of a problem reading the stream
    * @throws ClassNotFoundException in the event of classes that comprise the element list of this Fqn not being found
    * @since 2.2.0
    */
   public static Fqn<?> fromExternalStream(ObjectInput in) throws IOException, ClassNotFoundException {
      Fqn<?> f = new Fqn<Object>();
      f.readExternal(in);
      return f;
   }


   /**
    * Obtains an ancestor of the current Fqn.  Literally performs <code>elements.subList(0, generation)</code> such that
    * if <code> generation == Fqn.size() </code> then the return value is the Fqn itself (current generation), and if
    * <code> generation == Fqn.size() - 1 </code> then the return value is the same as <code> Fqn.getParent() </code>
    * i.e., just one generation behind the current generation. <code> generation == 0 </code> would return Fqn.ROOT.
    *
    * @param generation the generation of the ancestor to retrieve
    * @return an ancestor of the current Fqn
    */
   public Fqn<E> getAncestor(int generation) {
      if (generation == 0) return root();
      return getSubFqn(0, generation);
   }

   /**
    * Obtains a sub-Fqn from the given Fqn.  Literally performs <code>elements.subList(startIndex, endIndex)</code>
    *
    * @param startIndex starting index
    * @param endIndex   end index
    * @return a subFqn
    */
   public Fqn<E> getSubFqn(int startIndex, int endIndex) {
      if (endIndex < startIndex) throw new IllegalArgumentException("End index cannot be less than start index!");
      int len = endIndex - startIndex;
      Object[] subElements = new Object[len];
      System.arraycopy(elements, startIndex, subElements, 0, len);
      return new Fqn<E>(subElements, true);
   }

   /**
    * @return the number of elements in the Fqn.  The root node contains zero.
    */
   public int size() {
      return elements.length;
   }

   /**
    * @param n index of the element to return
    * @return Returns the nth element in the Fqn.
    */
   public Object get(int n) {
      return elements[n];
   }

   /**
    * @return the last element in the Fqn.
    * @see #getLastElementAsString
    */
   public Object getLastElement() {
      if (isRoot()) return null;
      return elements[elements.length - 1];
   }

   /**
    * @param element element to find
    * @return true if the Fqn contains this element, false otherwise.
    */
   public boolean hasElement(Object element) {
      return indexOf(element) != -1;
   }

   /**
    * Returns true if obj is a Fqn with the same elements.
    */
   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof Fqn)) {
         return false;
      }
      Fqn<?> other = (Fqn<?>) obj;
      if (elements.length != other.elements.length) return false;
      // compare elements in *reverse*!
      for (int i=elements.length - 1; i>=0; i--)
      {
         if (!Util.safeEquals(elements[i], other.elements[i])) return false;
      }
      return true;
   }

   /**
    * Returns a hash code with Fqn elements.
    */
   @Override
   public int hashCode() {
      if (hash_code == 0) {
         hash_code = calculateHashCode();
      }
      return hash_code;
   }

   /**
    * Returns this Fqn as a string, prefixing the first element with a {@link Fqn#SEPARATOR} and joining each subsequent
    * element with a {@link Fqn#SEPARATOR}. If this is the root Fqn, returns {@link Fqn#SEPARATOR}. Example:
    * <pre>
    * new Fqn(new Object[] { "a", "b", "c" }).toString(); // "/a/b/c"
    * Fqn.ROOT.toString(); // "/"
    * </pre>
    */
   @Override
   public String toString() {
      if (stringRepresentation == null) {
         stringRepresentation = getStringRepresentation(elements);
      }
      return stringRepresentation;
   }

   public void writeExternal(ObjectOutput out) throws IOException {
      out.writeShort(elements.length);
      for (Object element : elements) {
         out.writeObject(element);
      }
   }

   public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
      int size = in.readShort();
      this.elements = new Object[size];
      for (int i = 0; i < size; i++) elements[i] = in.readObject();
   }


   /**
    * Returns true if this Fqn is child of parentFqn. Example usage:
    * <pre>
    * Fqn<String> f1 = Fqn.fromString("/a/b");
    * Fqn<String> f2 = Fqn.fromString("/a/b/c");
    * assertTrue(f1.isChildOf(f2));
    * assertFalse(f1.isChildOf(f1));
    * assertFalse(f2.isChildOf(f1));
    * </pre>
    *
    * @param parentFqn candidate parent to test against
    * @return true if the target is a child of parentFqn
    */
   public boolean isChildOf(Fqn<? super E> parentFqn) {
      return parentFqn.size() != elements.length && isChildOrEquals(parentFqn);
   }


   /**
    * Returns true if this Fqn is a <i>direct</i> child of a given Fqn.
    *
    * @param parentFqn parentFqn to compare with
    * @return true if this is a direct child, false otherwise.
    */
   public boolean isDirectChildOf(Fqn<? super E> parentFqn) {
      return elements.length == parentFqn.size() + 1 && isChildOf(parentFqn);
   }

   /**
    * Returns true if this Fqn is equals or the child of parentFqn. Example usage:
    * <pre>
    * Fqn<String> f1 = Fqn.fromString("/a/b");
    * Fqn<String> f2 = Fqn.fromString("/a/b/c");
    * assertTrue(f1.isChildOrEquals(f2));
    * assertTrue(f1.isChildOrEquals(f1));
    * assertFalse(f2.isChildOrEquals(f1));
    * </pre>
    *
    * @param parentFqn candidate parent to test against
    * @return true if this Fqn is equals or the child of parentFqn.
    */
   public boolean isChildOrEquals(Fqn<? super E> parentFqn) {
      Object[] parentElems = parentFqn.elements;
      if (parentElems.length > elements.length) {
         return false;
      }
      for (int i = parentElems.length - 1; i >= 0; i--) {
         if (!parentElems[i].equals(elements[i])) {
            return false;
         }
      }
      return true;
   }

   /**
    * Calculates a hash code by summing the hash code of all elements.
    *
    * @return a calculated hashcode
    */
   protected int calculateHashCode() {
      int hashCode = 19;
      for (Object o : elements) hashCode = 31 * hashCode + (o == null ? 0 : o.hashCode());
      if (hashCode == 0) hashCode = 0xDEADBEEF; // degenerate case
      return hashCode;
   }

   protected String getStringRepresentation(List<E> elements) {
      return getStringRepresentation(elements.toArray());
   }

   protected String getStringRepresentation(Object[] elements) {
      StringBuilder builder = new StringBuilder();
      for (Object e : elements) {
         // incase user element 'e' does not implement equals() properly, don't rely on their implementation.
         if (!SEPARATOR.equals(e) && !"".equals(e)) {
            builder.append(SEPARATOR);
            builder.append(e);
         }
      }
      return builder.length() == 0 ? SEPARATOR : builder.toString();
   }


   /**
    * Returns the parent of this Fqn. The parent of the root node is {@link #ROOT}. Examples:
    * <pre>
    * Fqn<String> f1 = Fqn.fromString("/a");
    * Fqn<String> f2 = Fqn.fromString("/a/b");
    * assertEquals(f1, f2.getParent());
    * assertEquals(Fqn.ROOT, f1.getParent().getParent());
    * assertEquals(Fqn.ROOT, Fqn.ROOT.getParent());
    * </pre>
    *
    * @return the parent Fqn
    */
   public Fqn<E> getParent() {
      switch (elements.length) {
         case 0:
         case 1:
            return root();
         default:
            return getSubFqn(0, elements.length - 1);
      }
   }

   public static final <T> Fqn<T> root()  // declared final so compilers can optimise and in-line.
   {
      return ROOT;
   }

   /**
    * Returns true if this is a root Fqn.
    *
    * @return true if the Fqn is Fqn.ROOT.
    */
   public boolean isRoot() {
      return elements.length == 0;
   }

   /**
    * If this is the root, returns {@link Fqn#SEPARATOR}.
    *
    * @return a String representation of the last element that makes up this Fqn.
    */
   public String getLastElementAsString() {
      if (isRoot()) {
         return SEPARATOR;
      } else {
         Object last = getLastElement();
         if (last instanceof String)
            return (String) last;
         else
            return String.valueOf(getLastElement());
      }
   }

   /**
    * Peeks into the elements that build up this Fqn.  The list returned is read-only, to maintain the immutable nature
    * of Fqn.
    *
    * @return an unmodifiable list
    */
   @SuppressWarnings("unchecked")
   public List<E> peekElements() {
      return (List<E>) Arrays.asList(elements);
   }

   private int indexOf(Object o) {
      if (o == null) {
         for (int i = 0; i < elements.length; i++)
            if (elements[i] == null)
               return i;
      } else {
         for (int i = 0; i < elements.length; i++)
            if (o.equals(elements[i]))
               return i;
      }
      return -1;
   }

   /**
    * Compares this Fqn to another using {@link FqnComparator}.
    */
   public int compareTo(Fqn<?> fqn) {
      return FqnComparator.INSTANCE.compare(this, fqn);
   }

   /**
    * Creates a new Fqn whose ancestor has been replaced with the new ancestor passed in.
    *
    * @param oldAncestor old ancestor to replace
    * @param newAncestor nw ancestor to replace with
    * @return a new Fqn with ancestors replaced.
    */
   public Fqn<E> replaceAncestor(Fqn<E> oldAncestor, Fqn<E> newAncestor) {
      if (!isChildOf(oldAncestor))
         throw new IllegalArgumentException("Old ancestor must be an ancestor of the current Fqn!");
      Fqn<E> subFqn = this.getSubFqn(oldAncestor.size(), size());
      return Fqn.fromRelativeFqn(newAncestor, subFqn);
   }
}