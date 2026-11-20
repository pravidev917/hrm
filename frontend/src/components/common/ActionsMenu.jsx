import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import Icon from './Icon'

const MENU_WIDTH = 216

/**
 * Three-dot row menu.
 *
 * Rendered in a portal so a scroll container (the table wrapper) cannot clip it,
 * positioned next to its trigger and kept inside the viewport on phones.
 *
 * Keyboard: Enter/Space/ArrowDown opens and focuses the first item, Up/Down move,
 * Home/End jump, Escape closes and returns focus to the trigger, Tab closes.
 * Mouse/touch: click an item, or click anywhere else to dismiss.
 *
 * items: [{ key, label, icon, hint, tone: 'default' | 'danger', disabled, onSelect }]
 */
export default function ActionsMenu({ label, items }) {
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState({ top: 0, left: 0 })
  const triggerRef = useRef(null)
  const menuRef = useRef(null)
  const itemRefs = useRef([])
  const menuId = useId()

  const enabledItems = items.filter((item) => !item.disabled)

  const place = useCallback(() => {
    const trigger = triggerRef.current
    if (!trigger) return
    const rect = trigger.getBoundingClientRect()
    const left = Math.min(
      Math.max(8, rect.right - MENU_WIDTH),
      Math.max(8, window.innerWidth - MENU_WIDTH - 8),
    )
    // Flip above the trigger when there is not enough room below.
    const openUp = rect.bottom + 260 > window.innerHeight && rect.top > 260
    setPosition({
      top: openUp ? undefined : rect.bottom + 6,
      bottom: openUp ? window.innerHeight - rect.top + 6 : undefined,
      left,
    })
  }, [])

  const close = useCallback(
    (returnFocus = false) => {
      setOpen(false)
      if (returnFocus) triggerRef.current?.focus()
    },
    [],
  )

  useEffect(() => {
    if (!open) return

    // Focus the first usable item once the menu is on screen.
    const first = itemRefs.current.find((el) => el && !el.disabled)
    first?.focus()

    const onPointerDown = (event) => {
      if (menuRef.current?.contains(event.target) || triggerRef.current?.contains(event.target)) return
      setOpen(false)
    }
    const onKeyDown = (event) => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        close(true)
        return
      }
      if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp' && event.key !== 'Home' && event.key !== 'End') {
        if (event.key === 'Tab') setOpen(false)
        return
      }

      event.preventDefault()
      const focusable = itemRefs.current.filter((el) => el && !el.disabled)
      if (focusable.length === 0) return
      const index = focusable.indexOf(document.activeElement)
      let next = 0
      if (event.key === 'ArrowDown') next = index < 0 ? 0 : (index + 1) % focusable.length
      if (event.key === 'ArrowUp') next = index <= 0 ? focusable.length - 1 : index - 1
      if (event.key === 'End') next = focusable.length - 1
      focusable[next].focus()
    }
    const onReflow = () => setOpen(false)

    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('touchstart', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    window.addEventListener('resize', onReflow)
    window.addEventListener('scroll', onReflow, true)

    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('touchstart', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('resize', onReflow)
      window.removeEventListener('scroll', onReflow, true)
    }
  }, [open, close])

  function toggle() {
    if (open) {
      close()
      return
    }
    place()
    setOpen(true)
  }

  function onTriggerKeyDown(event) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      place()
      setOpen(true)
    }
  }

  function select(item) {
    if (item.disabled) return
    setOpen(false)
    item.onSelect()
  }

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className="btn btn-sm btn-ghost btn-icon actions-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        aria-label={label ?? 'Row actions'}
        title={label ?? 'Row actions'}
        onClick={toggle}
        onKeyDown={onTriggerKeyDown}
      >
        <Icon name="more" size={18} />
      </button>

      {open &&
        createPortal(
          <div
            ref={menuRef}
            id={menuId}
            role="menu"
            aria-label={label ?? 'Row actions'}
            className="menu actions-menu"
            style={{ top: position.top, bottom: position.bottom, left: position.left, width: MENU_WIDTH }}
          >
            {items.map((item, index) => (
              <button
                key={item.key}
                ref={(el) => {
                  itemRefs.current[index] = el
                }}
                type="button"
                role="menuitem"
                className={`menu-item${item.tone === 'danger' ? ' menu-item--danger' : ''}`}
                disabled={item.disabled}
                title={item.disabled ? item.hint : undefined}
                onClick={() => select(item)}
              >
                {item.icon && <Icon name={item.icon} size={16} />}
                <span className="menu-item-text">
                  {item.label}
                  {item.disabled && item.hint && <span className="menu-item-hint">{item.hint}</span>}
                </span>
              </button>
            ))}
            {enabledItems.length === 0 && <p className="menu-empty">No actions available</p>}
          </div>,
          document.body,
        )}
    </>
  )
}
