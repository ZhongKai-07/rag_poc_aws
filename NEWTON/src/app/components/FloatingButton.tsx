import { motion } from "motion/react";
import { Bot } from "lucide-react";
import { useState, useRef, useEffect } from "react";
// Removed figma asset import
// import botIcon from "figma:asset/27f58b3bbbd18d9ecbe925d4e9093be37a9712b3.png";

interface FloatingButtonProps {
  onDoubleClick: () => void; // 保留prop名称以兼容，但实际用作onClick
}

export function FloatingButton({ onDoubleClick }: FloatingButtonProps) {
  const avatarImage = "https://images.unsplash.com/photo-1557683316-973673baf926?q=80&w=200&auto=format&fit=crop";
  const [position, setPosition] = useState({ x: window.innerWidth - 80, y: window.innerHeight - 80 });
  const [isDragging, setIsDragging] = useState(false);
  const [isDocked, setIsDocked] = useState(false);
  const [showDockZone, setShowDockZone] = useState(false);
  const [hasMoved, setHasMoved] = useState(false);
  const dragStartRef = useRef({ x: 0, y: 0 });

  useEffect(() => {
    const handleResize = () => {
      if (!isDocked) {
        setPosition({ x: window.innerWidth - 80, y: window.innerHeight - 80 });
      }
    };
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, [isDocked]);

  const handleMouseDown = (e: React.MouseEvent) => {
    setIsDragging(true);
    setHasMoved(false);
    dragStartRef.current = {
      x: e.clientX - position.x,
      y: e.clientY - position.y,
    };
  };

  const handleMouseMove = (e: MouseEvent) => {
    if (!isDragging) return;
    const newX = e.clientX - dragStartRef.current.x;
    const newY = e.clientY - dragStartRef.current.y;
    
    // Constrain to viewport
    const constrainedX = Math.max(0, Math.min(newX, window.innerWidth - 56));
    const constrainedY = Math.max(0, Math.min(newY, window.innerHeight - 56));
    
    setPosition({ x: constrainedX, y: constrainedY });

    // Check if near bottom center for docking
    const bottomCenter = window.innerWidth / 2;
    const isNearBottom = e.clientY > window.innerHeight - 100;
    const isNearCenter = Math.abs(e.clientX - bottomCenter) < 200;
    setShowDockZone(isNearBottom && isNearCenter);
    setHasMoved(true);
  };

  const handleMouseUp = (e: MouseEvent) => {
    if (!isDragging) return;
    setIsDragging(false);
    
    // Check if should dock
    const bottomCenter = window.innerWidth / 2;
    const isNearBottom = e.clientY > window.innerHeight - 100;
    const isNearCenter = Math.abs(e.clientX - bottomCenter) < 200;
    
    if (isNearBottom && isNearCenter) {
      setIsDocked(true);
      setShowDockZone(false);
    } else {
      setIsDocked(false);
    }
  };

  useEffect(() => {
    if (isDragging) {
      window.addEventListener("mousemove", handleMouseMove);
      window.addEventListener("mouseup", handleMouseUp);
      return () => {
        window.removeEventListener("mousemove", handleMouseMove);
        window.removeEventListener("mouseup", handleMouseUp);
      };
    }
  }, [isDragging]);

  if (isDocked) {
    return (
      <>
        <motion.button
          className="fixed bottom-6 left-1/2 -translate-x-1/2 h-12 px-4 bg-primary text-primary-foreground rounded-full shadow-lg flex items-center gap-2 hover:bg-primary/90 transition-colors z-50 group"
          onClick={(e) => {
            if (!hasMoved) {
              e.stopPropagation();
              onDoubleClick();
            }
          }}
          onMouseDown={(e) => {
            // Allow dragging from docked position
            setIsDocked(false);
            const rect = e.currentTarget.getBoundingClientRect();
            setPosition({ x: rect.left, y: rect.top });
            dragStartRef.current = {
              x: e.clientX - rect.left,
              y: e.clientY - rect.top,
            };
            setIsDragging(true);
            setHasMoved(false);
          }}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          whileHover={{ scale: 1.02 }}
          whileTap={{ scale: 0.98 }}
          title="點擊打開 / Click to open"
        >
          <div className="w-8 h-8 flex items-center justify-center">
            <img src={avatarImage} alt="AI Assistant" className="w-full h-full object-contain" />
          </div>
          <span className="font-medium">ICOA</span>
        </motion.button>
      </>
    );
  }

  return (
    <>
      {/* Dock Zone Indicator */}
      {showDockZone && (
        <motion.div
          className="fixed bottom-4 left-1/2 -translate-x-1/2 w-32 h-14 border-2 border-dashed border-primary/40 rounded-full bg-primary/5 z-40"
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
        />
      )}

      {/* Floating Button */}
      <motion.button
        className="fixed w-16 h-16 bg-white text-primary-foreground rounded-full shadow-xl flex items-center justify-center hover:scale-110 transition-transform z-50 cursor-move"
        style={{
          left: position.x,
          top: position.y,
          opacity: isDragging ? 0.7 : 1,
        }}
        onMouseDown={handleMouseDown}
        onClick={(e) => {
          if (!hasMoved) {
            e.stopPropagation();
            onDoubleClick();
          }
        }}
        whileHover={{ scale: 1.1 }}
        whileTap={{ scale: 0.95 }}
        title="點擊打開 / Click to open"
      >
        <div className="w-12 h-12 flex items-center justify-center">
          <img src={avatarImage} alt="AI Assistant" className="w-full h-full object-contain" />
        </div>
      </motion.button>
    </>
  );
}