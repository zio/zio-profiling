package zio

package object profiling {
  private[profiling] val CostCenterRef: FiberRef[CostCenter] =
    Unsafe.unsafeCompat(implicit u => FiberRef.unsafe.make(CostCenter.Root, identity, (old, _) => old))
}
