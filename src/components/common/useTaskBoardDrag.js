import { ref } from 'vue'

export function useTaskBoardDrag({ normalizeStatus, emitStatusChange }) {
  const isStatusTransitionInFlight = ref(false)
  const draggingTask = ref(null)
  const dragTransitionEmitted = ref(false)
  let mouseDragCandidate = null

  const cleanupMouseDrag = () => {
    window.removeEventListener('mousemove', handleWindowMouseMove)
    window.removeEventListener('mouseup', handleWindowMouseUp)
    mouseDragCandidate = null
    draggingTask.value = null
    dragTransitionEmitted.value = false
  }

  const emitDragStatusChange = (task, targetColumnKey) => {
    if (!task) return
    if (dragTransitionEmitted.value) return
    if (normalizeStatus(task.status) === targetColumnKey) return
    dragTransitionEmitted.value = true
    emitStatusChange(task, targetColumnKey)
  }

  const onDragChange = (evt, targetColumnKey) => {
    if (!evt?.added) return
    emitDragStatusChange(evt.added.element, targetColumnKey)
  }

  function handleWindowMouseMove(event) {
    if (!mouseDragCandidate || mouseDragCandidate.active) return
    const deltaX = Math.abs(event.clientX - mouseDragCandidate.startX)
    const deltaY = Math.abs(event.clientY - mouseDragCandidate.startY)
    if (deltaX < 6 && deltaY < 6) return
    mouseDragCandidate.active = true
    draggingTask.value = mouseDragCandidate.task
  }

  function handleWindowMouseUp() {
    cleanupMouseDrag()
  }

  const handleMouseDragStart = (task, event) => {
    if (event?.button !== 0) return
    mouseDragCandidate = {
      task,
      startX: event.clientX,
      startY: event.clientY,
      active: false,
    }
    dragTransitionEmitted.value = false
    window.addEventListener('mousemove', handleWindowMouseMove)
    window.addEventListener('mouseup', handleWindowMouseUp)
  }

  const handleMouseDrop = (targetColumnKey) => {
    if (!mouseDragCandidate?.active) return
    emitDragStatusChange(mouseDragCandidate.task, targetColumnKey)
    cleanupMouseDrag()
  }

  return {
    isStatusTransitionInFlight,
    onDragChange,
    handleMouseDragStart,
    handleMouseDrop,
  }
}
