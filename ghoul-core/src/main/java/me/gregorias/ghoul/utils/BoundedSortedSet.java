package me.gregorias.ghoul.utils;

import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.SortedSet;

/**
 * SortedSet that has limited size. Upon receival of an object it discards the largest one if the
 * size is greater than bound.
 */
public class BoundedSortedSet<E> extends AbstractSet<E> implements SortedSet<E> {
  private final SortedSet<E> mBase;
  private final int mUpperCountBound;

  public BoundedSortedSet(SortedSet<E> base, int upperCountBound) {
    mBase = base;
    mUpperCountBound = upperCountBound;
  }

  @Override
  public boolean add(E element) {
    boolean hasBeenAdded = mBase.add(element);
    if (!hasBeenAdded) {
      return hasBeenAdded;
    }

    if (mBase.size() > mUpperCountBound) {
      assert mBase.size() == mUpperCountBound + 1;
      E lastElement = mBase.last();
      mBase.remove(lastElement);
      return mBase.comparator().compare(element, lastElement) == 0;
    } else {
      return true;
    }
  }

  @Override
  public Comparator<? super E> comparator() {
    return mBase.comparator();
  }

  @Override
  public E first() {
    return mBase.first();
  }

  @Override
  public SortedSet<E> headSet(E toElement) {
    return mBase.headSet(toElement);
  }

  @Override
  public Iterator<E> iterator() {
    return mBase.iterator();
  }

  @Override
  public E last() {
    return mBase.last();
  }

  @Override
  public int size() {
    return mBase.size();
  }

  @Override
  public SortedSet<E> subSet(E fromElement, E toElement) {
    return mBase.subSet(fromElement, toElement);
  }

  @Override
  public SortedSet<E> tailSet(E fromElement) {
    return mBase.tailSet(fromElement);
  }
}
