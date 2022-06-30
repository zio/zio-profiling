package zio

package object profiling {
  private[profiling] val TagRef: FiberRef[Tag] =
    Unsafe.unsafeCompat(implicit u => FiberRef.unsafe.make(Tag.Root, identity, (old, _) => old))
}
