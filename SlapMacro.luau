-- ============================================================================
-- Slap Battles Macro - Tryhard v9.1 (Safe Improved)
-- ============================================================================
-- Mejoras seguras (no rompe compatibilidad con config v9):
--   • Predicción con reset limpio al cambiar de target + filtro de outliers
--   • Slap State Machine con timeouts y cancelaciones robustas
--   • Anti-Counter / Anti-Megarock más reactivos + límite de tiempo de reequip
--   • Pathfinding + colisiones más estables
--   • Spacing / CenterBias más inteligente cerca de bordes
--   • Política de tools 100% segura (nunca quita tools ajenas)
--   • Debug Mode + Status más claro
--   • Limpieza de código y menos duplicación
--   • Ability loop más seguro
--
-- Estructura del archivo (busca por estos marcadores):
--   [SERVICES]          Servicios de Roblox
--   [PLAYER]            Jugador local y nombre de config
--   [MAP]               Mapa de Main Island (centro + edges)
--   [SETTINGS]          Tabla de configuración editable
--   [RUNTIME]           Variables de estado en tiempo de ejecución
--   [HELPERS]           Funciones auxiliares (char, target, gloves, etc.)
--   [MAP MATH]          Distancia plana, edges, dirección de fling
--   [MOVEMENT]          MoveTo, Orient, Face, Jump, EdgeSafe
--   [PREDICTION]        Velocidad suavizada + predicción Simple/Intercept
--   [COMBAT POS]        Posición ideal de pelea, fases, chase
--   [ABILITY]           Trigger de habilidad (E)
--   [SERVER HOP]        Cambio de servidor
--   [TOURNAMENT]        Auto accept/decline del prompt de torneo
--   [UI]                Interfaz (tabs, toggles, sliders)
--   [MAIN LOOP]         Heartbeat principal
--   [ABILITY LOOP]      Loop secundario de AutoAbility
-- ============================================================================


-- ============================================================================
-- [SERVICES]
-- ============================================================================
local Players             = game:GetService("Players")
local RunService          = game:GetService("RunService")
local TeleportService     = game:GetService("TeleportService")
local HttpService         = game:GetService("HttpService")
local VirtualInputManager = game:GetService("VirtualInputManager")
local PathfindingService  = game:GetService("PathfindingService")
local GuiService          = game:GetService("GuiService")
local ReplicatedStorage   = game:GetService("ReplicatedStorage")


-- ============================================================================
-- [PLAYER]
-- ============================================================================
local Player    = Players.LocalPlayer
local PlayerGui = Player:WaitForChild("PlayerGui")
local FileName  = "MacroGloveConfig_Tryhard_v9.json"   -- misma config que v9 (compatible)


-- ============================================================================
-- [MAP]  Main Island – centro y bordes (anti-void / fling outward)
-- ============================================================================
local IslandMap_Main = {
	center = Vector3.new(0.4, -5.2, -2.3),
	radius = 115,
	edges = {
		Vector3.new( 94.2, -5.2,  59.8),
		Vector3.new(108.1, -5.2,  15.8),
		Vector3.new(105.1, -5.2, -28.1),
		Vector3.new( 87.0, -5.2, -66.5),
		Vector3.new( 53.6, -5.2, -95.2),
		Vector3.new( 27.4, -5.2,-106.8),
		Vector3.new(-17.6, -5.2,-110.4),
		Vector3.new(-55.5, -5.2, -99.3),
		Vector3.new(-89.6, -5.2, -72.6),
		Vector3.new(-111.6,-5.2, -33.1),
		Vector3.new(-115.4,-5.2,  18.4),
		Vector3.new(-108.7,-5.2,  44.6),
		Vector3.new( -96.5,-5.2,  68.6),
		Vector3.new( -72.8,-5.2,  92.4),
		Vector3.new( -44.8,-5.2, 107.9),
		Vector3.new( -13.0,-5.2, 115.1),
		Vector3.new(  12.9,-5.2, 113.5),
		Vector3.new(  41.4,-5.2, 105.1),
		Vector3.new(  70.1,-5.2,  88.3),
		Vector3.new(  84.0,-5.2,  73.3),
	},
}


-- ============================================================================
-- [SETTINGS]  Configuración persistente (compatible 100% con v9)
-- ============================================================================
local Settings = {
	-- General
	Enabled              = false,
	BotType              = "Tryhard",   -- Tryhard | Closest | LowestHP | Furthest | Random
	MovementMode         = "Full",      -- Full | Legal
	PreferArena          = true,
	IgnoreRagdoll        = true,
	IgnoreOneshots       = true,
	IgnoreSpectator      = true,
	PauseWhenUnequipped  = true,
	DebugMode            = false,       -- NUEVO (seguro)

	-- Focus / Whitelist
	FocusEnabled         = true,
	FocusMode            = "Prefer",    -- Prefer | Only
	FocusList            = {},
	WhitelistEnabled     = true,
	Whitelist            = {},

	-- Anti Counter / Megarock
	AntiMegarock         = true,
	AntiCounter          = true,
	CounterAnimID        = "13552944381",
	MegarockSafeDist     = 16,
	ForceReequipMaxTime  = 1.25,        -- NUEVO (seguro)

	-- Slap básico
	SlapRadius           = 11.5,
	EngageRadius         = 4.5,
	BackOffAfterSlap     = true,
	BackOffDistance      = 6.5,
	SlapCooldown         = 0.12,
	FlickAimTime         = 0.04,
	FlickLockTime        = 0.22,
	MinSlapSeparation    = 2.2,

	-- Orientación
	FaceTarget           = true,
	ShiftlockRadius      = 50,

	-- Strafe / Orbit
	StrafeEnabled        = true,
	StrafeRadius         = 16,
	StrafeSpeed          = 4.2,
	OrbitDistance        = 6,
	OrbitJitter          = true,

	-- Jumps
	Jumps                = true,
	JumpChance           = 18,
	JumpOnEngage         = true,

	-- Tryhard clásico
	FakeFlick            = true,
	EdgeSafe             = true,
	EdgeDangerDist       = 14,
	NoChaseIntoEdge      = true,
	CenterBias           = true,
	CenterBiasStrength   = 0.55,
	PredictMovement      = true,
	PredictMode          = "Intercept", -- Simple | Intercept
	PredictLead          = 0.16,
	FlingOutwardWeight   = 0.72,
	PredictAggressiveness = 1.0,        -- NUEVO (0.6 – 1.4 recomendado)

	-- Tryhard v2 (universal)
	TV2_Enabled          = true,
	TV2_Spacing          = true,
	TV2_AngularApproach  = true,
	TV2_LateralOffset    = 3.5,
	TV2_SlapDirecta      = true,
	TV2_DoubleSlap       = true,
	TV2_DoubleSlapDelay  = 0.11,
	TV2_VelocitySmooth   = true,
	TV2_MySpeed          = 18,
	TV2_EngageDist       = 15,
	TV2_StrikeDist       = 10.5,

	-- Unequip / Danger
	UnequipFar           = false,
	UnequipRadius        = 40,
	DangerMode           = true,
	DangerType           = "Backoff",   -- Backoff | Give up | Ahh
	DangerHP             = 35,

	-- Ability
	AutoAbility          = false,
	AbilityMode          = "Combat",    -- Combat | Defensive | Instant | Combo | Camping
	AbilityCooldown      = 1.1,

	-- Stuck / Server hop
	StuckTimeout         = 35,
	ServerHopMode        = "Main",      -- Main | NoOneshots | KS
	MinPlayers           = 0,
	IgnoreLowPlayers     = false,
	AutoHopTimerEnabled  = false,
	AutoHopMinutes       = 30,
	HopOnOneshotsEnabled = false,
	MaxOneshotsAllowed   = 2,
	LastHopTick          = tick(),
	AutoTournament       = false,
}


-- ============================================================================
-- [RUNTIME]  Estado mutable organizado (seguro)
-- ============================================================================
local Runtime = {
	LastSlapCount     = 0,
	LastReachedTime   = tick(),
	LastClickTick     = 0,
	LastAbilityTick   = 0,
	LastSlapActivate  = 0,
	lastJumpTick      = 0,
	StrafeAngle       = 0,
	lastPathTick      = 0,
	lastMovePos       = nil,
	justSlappedUntil  = 0,
	faceLockUntil     = 0,
	orbitJitterPhase  = 0,
	slapState         = nil,   -- nil | "aim" | "fire" | "double"
	slapStateT        = 0,
	slapWantJump      = false,
	cachedFlingDir    = nil,
	forceReequip      = false,
	forceReequipStart = 0,
	combatPhase       = "chase",  -- chase | engage | strike | backoff
	smoothVel         = Vector3.zero,
	lastVel           = Vector3.zero,
	lastTime          = 0,
	accelHistory      = {},
	lastTarget        = nil,
	lastPathValid     = false,
	lastPathPos       = nil,
}

-- Referencias UI
local StatusLabel
local DebugLabel


-- ============================================================================
-- [HELPERS]  Utilidades de personaje, stats y validación de targets
-- ============================================================================

local function GetSlaps()
	local stats = Player:FindFirstChild("leaderstats")
	local slaps = stats and stats:FindFirstChild("Slaps")
	return slaps and slaps.Value or 0
end
Runtime.LastSlapCount = GetSlaps()

local function GetGloveName()
	local stats = Player:FindFirstChild("leaderstats")
	local glove = stats and stats:FindFirstChild("Glove")
	return glove and glove.Value or nil
end

local function SaveSettings()
	local ok, encoded = pcall(function()
		return HttpService:JSONEncode(Settings)
	end)
	if ok and writefile then
		pcall(writefile, FileName, encoded)
	end
end

local function LoadSettings()
	if isfile and isfile(FileName) then
		local ok, decoded = pcall(function()
			return HttpService:JSONDecode(readfile(FileName))
		end)
		if ok and type(decoded) == "table" then
			for k, v in pairs(decoded) do
				if Settings[k] ~= nil then
					Settings[k] = v
				end
			end
		end
	end
	Settings.FocusList = Settings.FocusList or {}
	Settings.Whitelist = Settings.Whitelist or {}
end
LoadSettings()

local GameIDs = {
	Main       = 6403373529,
	NoOneshots = 9015014224,
	KS         = 11520107397,
}

local OneShotGloves = {
	"OVERKILL", "God's Hand", "The Flex", "Error", "rob",
	"Shopkeeper", "buddies", "Killstreak",
}

local SlowGloves = {
	brick = true, shard = true, diamond = true, megarock = true,
	rock = true, default = true, extended = true, snow = true,
	pull = true, flash = true, spring = true,
}

local function GetChar()
	return Player.Character
end

local function GetRoot(c)
	return c and c:FindFirstChild("HumanoidRootPart")
end

local function GetHum(c)
	return c and c:FindFirstChildOfClass("Humanoid")
end

local function NameMatches(list, playerName)
	local lower = string.lower(playerName)
	for _, entry in ipairs(list) do
		local e = string.lower(tostring(entry))
		if e ~= "" and (lower == e or string.find(lower, e, 1, true)) then
			return true
		end
	end
	return false
end

local function GetPlayerGlove(p)
	local ls = p:FindFirstChild("leaderstats")
	local g  = ls and ls:FindFirstChild("Glove")
	return g and tostring(g.Value) or nil
end

local function HasOneShot(p)
	local c     = p.Character
	local bp    = p:FindFirstChild("Backpack")
	local glove = GetPlayerGlove(p)
	for _, g in ipairs(OneShotGloves) do
		if (c and c:FindFirstChild(g)) or (bp and bp:FindFirstChild(g)) then
			return true
		end
		if glove and glove == g then
			return true
		end
	end
	return false
end

local function IsRagdolled(c)
	local r = c and c:FindFirstChild("Ragdolled")
	return r and r.Value == true
end

local function TargetIsCountering(targetPlayer)
	if not Settings.AntiCounter or not targetPlayer or not targetPlayer.Character then
		return false
	end
	local hum = GetHum(targetPlayer.Character)
	if not hum then return false end
	local animator = hum:FindFirstChildOfClass("Animator")
	if not animator then return false end
	local id = tostring(Settings.CounterAnimID)
	for _, track in ipairs(animator:GetPlayingAnimationTracks()) do
		if track.Animation and string.find(tostring(track.Animation.AnimationId), id, 1, true) then
			return true
		end
	end
	return false
end

local function GetTargetRock(targetPlayer)
	if not Settings.AntiMegarock or not targetPlayer then
		return nil
	end
	local function findRock(container)
		if not container then return nil end
		local rock = container:FindFirstChild("rock")
		if rock and (rock:IsA("BasePart") or rock:IsA("Model")) then
			return rock
		end
		return nil
	end
	local rock = findRock(workspace:FindFirstChild(targetPlayer.Name))
	if rock then return rock end
	return findRock(targetPlayer.Character)
end

local function RockPosition(rock)
	if rock:IsA("Model") then
		return rock:GetPivot().Position
	end
	return rock.Position
end

local function IsValidTarget(p)
	if not p or p == Player then return false end
	local c = p.Character
	if not c then return false end
	local h, root = GetHum(c), GetRoot(c)
	if not h or h.Health <= 0 or not root then return false end
	if Settings.WhitelistEnabled and NameMatches(Settings.Whitelist, p.Name) then
		return false
	end
	local glove = GetPlayerGlove(p)
	if Settings.IgnoreSpectator and glove and string.lower(glove) == "spectator" then
		return false
	end
	if c:FindFirstChild("stevebody") or c:FindFirstChild("Mirage") then
		return false
	end
	if Settings.PreferArena and not c:FindFirstChild("entered") then
		return false
	end
	if Settings.IgnoreRagdoll and IsRagdolled(c) then
		return false
	end
	if Settings.IgnoreOneshots and HasOneShot(p) then
		return false
	end
	if root.BrickColor == BrickColor.new("New Yeller") then
		return false
	end
	if c:FindFirstChild("Head") and c.Head:FindFirstChild("UnoReverseCard") then
		return false
	end
	return true
end

local function ScoreTarget(entry)
	local score = -entry.dist * 1.35 - entry.hp * 0.4
	local g = GetPlayerGlove(entry.player)
	if g and SlowGloves[string.lower(g)] then
		score += 30
	end
	if Settings.FocusEnabled and NameMatches(Settings.FocusList, entry.player.Name) then
		score += 280
	end
	if entry.dist < 20 and entry.hp < 50 then
		score += 18
	end
	return score
end

local function GetTarget()
	local myRoot = GetRoot(GetChar())
	if not myRoot then return nil end

	local list = {}
	for _, p in ipairs(Players:GetPlayers()) do
		if IsValidTarget(p) then
			local root = GetRoot(p.Character)
			local hp   = GetHum(p.Character).Health
			local dist = (myRoot.Position - root.Position).Magnitude
			table.insert(list, { player = p, root = root, dist = dist, hp = hp })
		end
	end
	if #list == 0 then return nil end

	-- Solo focus si modo Only
	if Settings.FocusEnabled and Settings.FocusMode == "Only" and #Settings.FocusList > 0 then
		local focused = {}
		for _, e in ipairs(list) do
			if NameMatches(Settings.FocusList, e.player.Name) then
				table.insert(focused, e)
			end
		end
		if #focused == 0 then return nil end
		list = focused
	end

	if Settings.BotType == "Closest" then
		table.sort(list, function(a, b) return a.dist < b.dist end)
	elseif Settings.BotType == "Furthest" then
		table.sort(list, function(a, b) return a.dist > b.dist end)
	elseif Settings.BotType == "LowestHP" then
		table.sort(list, function(a, b)
			if a.hp == b.hp then return a.dist < b.dist end
			return a.hp < b.hp
		end)
	elseif Settings.BotType == "Tryhard" then
		table.sort(list, function(a, b) return ScoreTarget(a) > ScoreTarget(b) end)
	elseif Settings.BotType == "Random" then
		return list[math.random(1, #list)].player
	end

	-- Prefer focus al principio de la lista
	if Settings.FocusEnabled and Settings.FocusMode == "Prefer" and #Settings.FocusList > 0 then
		for _, e in ipairs(list) do
			if NameMatches(Settings.FocusList, e.player.Name) then
				return e.player
			end
		end
	end

	return list[1].player
end


-- ============================================================================
-- [MAP MATH]  Distancia 2D, edge más cercano, dirección de fling
-- ============================================================================

local function FlatDist(a, b)
	local dx, dz = a.X - b.X, a.Z - b.Z
	return math.sqrt(dx * dx + dz * dz)
end

local function FlatUnit(v)
	local f = Vector3.new(v.X, 0, v.Z)
	if f.Magnitude < 1e-4 then return nil end
	return f.Unit
end

local function GetNearestEdge(fromPos)
	local best, bestDist = nil, math.huge
	for _, e in ipairs(IslandMap_Main.edges) do
		local d = FlatDist(fromPos, e)
		if d < bestDist then
			bestDist = d
			best = e
		end
	end
	return best, bestDist
end

-- Dirección de knockback: mezcla outward (centro→rival) + hacia el edge más cercano
local function GetFlingDirection(root, tRoot)
	local center  = IslandMap_Main.center
	local outward = FlatUnit(tRoot.Position - center)
	if not outward then
		outward = FlatUnit(tRoot.Position - root.Position)
	end
	if not outward then
		return Vector3.new(0, 0, -1)
	end

	local edge = select(1, GetNearestEdge(tRoot.Position))
	local w = math.clamp(Settings.FlingOutwardWeight or 0.65, 0, 1)
	if edge then
		local toEdge = FlatUnit(edge - tRoot.Position)
		if toEdge then
			local mixed = outward * w + toEdge * (1 - w)
			local u = FlatUnit(mixed)
			if u then return u end
		end
	end
	return outward
end


-- ============================================================================
-- [MOVEMENT]  Pathfinding, orientación, saltos, edge-safe
-- ============================================================================

local cache = {
	center = nil,
	centerFlat = nil,
	lastUpdate = 0
}

local function GetCachedCenter()
	if tick() - cache.lastUpdate > 0.5 then
		cache.center = IslandMap_Main.center
		cache.centerFlat = Vector3.new(cache.center.X, 0, cache.center.Z)
		cache.lastUpdate = tick()
	end
	return cache.center, cache.centerFlat
end

local function IsInDangerousState(hum)
	if not hum then return false end
	local state = hum:GetState()
	return state == Enum.HumanoidStateType.Freefall
		or state == Enum.HumanoidStateType.FallingDown
		or state == Enum.HumanoidStateType.Swimming
end

local function AdjustForCollisions(root, targetPos)
	if Settings.MovementMode == "Legal" then return targetPos end

	local char = GetChar()
	if not char then return targetPos end

	local origin = root.Position + Vector3.new(0, 3, 0)
	local direction = (targetPos - origin)
	local distance = direction.Magnitude
	if distance < 1 then return targetPos end
	direction = direction.Unit

	local raycastParams = RaycastParams.new()
	raycastParams.FilterType = Enum.RaycastFilterType.Blacklist
	raycastParams.FilterDescendantsInstances = {char}

	local result = workspace:Raycast(origin, direction * distance, raycastParams)

	if result then
		local normal = result.Normal
		local flatNormal = Vector3.new(normal.X, 0, normal.Z)
		if flatNormal.Magnitude > 0.1 then
			flatNormal = flatNormal.Unit
			local newPos = targetPos + flatNormal * 2.2
			local newResult = workspace:Raycast(origin, (newPos - origin).Unit * distance, raycastParams)
			if newResult then
				newPos = origin + (newPos - origin).Unit * (distance * 0.82)
			end
			return newPos
		end
	end

	return targetPos
end

local function MoveToTarget(targetPos)
	if Settings.MovementMode == "Legal" then return end

	local char = GetChar()
	local hum  = GetHum(char)
	local root = GetRoot(char)
	if not hum or not root then return end

	-- Siempre ajustar colisiones en Full (mejora segura)
	local adjustedPos = AdjustForCollisions(root, targetPos)

	local dist = (root.Position - adjustedPos).Magnitude

	-- Movimiento directo si está cerca
	if dist <= 45 then
		Runtime.lastMovePos = adjustedPos
		hum:MoveTo(adjustedPos)
		return
	end

	-- Reutilizar path reciente si el target no se movió mucho
	if Runtime.lastPathValid and Runtime.lastPathPos and tick() - Runtime.lastPathTick < 0.28 then
		local pathDrift = (adjustedPos - Runtime.lastPathPos).Magnitude
		if pathDrift < 8 and Runtime.lastMovePos then
			hum:MoveTo(Runtime.lastMovePos)
			return
		end
	end

	if tick() - Runtime.lastPathTick < 0.32 then
		if Runtime.lastMovePos then
			hum:MoveTo(Runtime.lastMovePos)
		end
		return
	end
	Runtime.lastPathTick = tick()

	if dist > 150 then
		Runtime.lastMovePos = adjustedPos
		hum:MoveTo(adjustedPos)
		Runtime.lastPathValid = false
		return
	end

	local pathConfig = {
		AgentRadius     = 2.2,
		AgentHeight     = 5,
		AgentCanJump    = true,
		WaypointSpacing = 5,
		AgentCanClimb   = false,
	}

	if Settings.TV2_Enabled then
		pathConfig.AgentRadius = 2.0
		pathConfig.WaypointSpacing = 4
	end

	local path = PathfindingService:CreatePath(pathConfig)

	local ok = pcall(function()
		path:ComputeAsync(root.Position, adjustedPos)
	end)

	if ok and path.Status == Enum.PathStatus.Success then
		local wps = path:GetWaypoints()
		local targetIndex
		if Settings.TV2_Enabled then
			if dist > 80 then
				targetIndex = math.max(#wps - 2, 1)
			elseif dist > 50 then
				targetIndex = math.max(#wps - 1, 1)
			else
				targetIndex = math.min(3, #wps)
			end
		else
			targetIndex = math.min(3, #wps)
		end

		local wp = wps[targetIndex]
		if wp then
			Runtime.lastMovePos = wp.Position
			Runtime.lastPathPos = adjustedPos
			Runtime.lastPathValid = true
			hum:MoveTo(wp.Position)
			if wp.Action == Enum.PathWaypointAction.Jump then
				hum.Jump = true
			end
			return
		end
	end

	Runtime.lastMovePos = adjustedPos
	Runtime.lastPathValid = false
	hum:MoveTo(adjustedPos)
end

local function OrientToDirection(root, dir)
	local u = FlatUnit(dir)
	if not u then return end
	local look = root.Position + u * 20
	root.CFrame = CFrame.lookAt(root.Position, Vector3.new(look.X, root.Position.Y, look.Z))
end

local function FaceTarget(root, tPos)
	if tick() < Runtime.faceLockUntil then return end

	if not Settings.TV2_Enabled then
		root.CFrame = CFrame.lookAt(root.Position, Vector3.new(tPos.X, root.Position.Y, tPos.Z))
		return
	end

	local dir = (tPos - root.Position)
	local flatDir = Vector3.new(dir.X, 0, dir.Z)
	if flatDir.Magnitude < 0.1 then return end

	local lookPos = root.Position + flatDir.Unit * 20
	local targetCFrame = CFrame.lookAt(root.Position, Vector3.new(lookPos.X, root.Position.Y, lookPos.Z))

	local currentCFrame = root.CFrame
	local currentAngle = math.atan2(currentCFrame.LookVector.Z, currentCFrame.LookVector.X)
	local targetAngle = math.atan2(lookPos.Z - root.Position.Z, lookPos.X - root.Position.X)

	local angleDiff = (targetAngle - currentAngle) % (math.pi * 2)
	if angleDiff > math.pi then angleDiff = angleDiff - math.pi * 2 end

	if math.abs(angleDiff) > 0.28 then
		root.CFrame = targetCFrame
	else
		local smoothAngle = currentAngle + angleDiff * 0.58
		local smoothCFrame = CFrame.Angles(0, smoothAngle, 0) + root.Position
		root.CFrame = smoothCFrame
	end
end

local function TryJump(hum, force)
	if not hum or hum.Health <= 0 then return end
	if tick() - Runtime.lastJumpTick < (force and 0.25 or 0.45) then return end
	local state = hum:GetState()
	if state == Enum.HumanoidStateType.Freefall
		or state == Enum.HumanoidStateType.Jumping
		or state == Enum.HumanoidStateType.FallingDown
	then
		return
	end
	if hum.FloorMaterial == Enum.Material.Air and not force then return end
	Runtime.lastJumpTick = tick()
	hum.Jump = true
	pcall(function()
		hum:ChangeState(Enum.HumanoidStateType.Jumping)
	end)
end

local function MaybeSafeFromEdge(root)
	if not Settings.EdgeSafe or Settings.MovementMode == "Legal" then
		return false
	end
	local _, distToEdge = GetNearestEdge(root.Position)
	if distToEdge >= (Settings.EdgeDangerDist or 14) then
		return false
	end
	local center, centerFlat = GetCachedCenter()
	local toCenter = FlatUnit(centerFlat - root.Position)
	if toCenter then
		-- Más agresivo si estamos muy cerca del borde
		local strength = distToEdge < 8 and 28 or 22
		MoveToTarget(root.Position + toCenter * strength)
		return true
	end
	return false
end


-- ============================================================================
-- [PREDICTION]  Velocidad suavizada + predicción Simple / Intercept (con reset seguro)
-- ============================================================================

local function ResetPrediction()
	Runtime.smoothVel = Vector3.zero
	Runtime.lastVel = Vector3.zero
	Runtime.lastTime = 0
	Runtime.accelHistory = {}
end

local function GetAccurateVelocity(tRoot)
	local raw = tRoot.AssemblyLinearVelocity

	if not (Settings.TV2_Enabled and Settings.TV2_VelocitySmooth) and not Settings.PredictMovement then
		return raw
	end

	local currentTime = tick()
	local dt = currentTime - Runtime.lastTime

	-- Filtro de outliers de aceleración (mejora segura)
	if Settings.TV2_Enabled and dt > 0 and dt < 0.45 then
		local accel = (raw - Runtime.lastVel) / dt
		if accel.Magnitude < 180 then   -- ignorar aceleraciones absurdas
			table.insert(Runtime.accelHistory, accel)
			if #Runtime.accelHistory > 5 then
				table.remove(Runtime.accelHistory, 1)
			end
		end
	end

	Runtime.lastVel = raw
	Runtime.lastTime = currentTime

	local alpha = 0.28
	if Settings.TV2_Enabled and Settings.TV2_VelocitySmooth then
		alpha = 0.34
	end

	Runtime.smoothVel = Runtime.smoothVel:Lerp(raw, alpha)

	if Settings.TV2_Enabled and #Runtime.accelHistory > 0 then
		local avgAccel = Vector3.zero
		for _, a in ipairs(Runtime.accelHistory) do
			avgAccel = avgAccel + a
		end
		avgAccel = avgAccel / #Runtime.accelHistory
		Runtime.smoothVel = Runtime.smoothVel + avgAccel * 0.09

		local maxVel = 95
		if Runtime.smoothVel.Magnitude > maxVel then
			Runtime.smoothVel = Runtime.smoothVel.Unit * maxVel
		end
	end

	return Runtime.smoothVel
end

local function GetSmoothedVelocity(tRoot)
	return GetAccurateVelocity(tRoot)
end

local function PredictTargetPos(root, tRoot)
	if not Settings.PredictMovement then
		return tRoot.Position
	end

	local vel = GetAccurateVelocity(tRoot)
	local pos = tRoot.Position

	if vel.Magnitude < 0.25 then
		return pos
	end

	local flatDist = FlatDist(root.Position, pos)
	local baseLead = Settings.PredictLead or 0.14
	local aggressiveness = math.clamp(Settings.PredictAggressiveness or 1.0, 0.5, 1.6)

	local adaptiveLead = baseLead * aggressiveness
	if Settings.TV2_Enabled then
		adaptiveLead = math.clamp(flatDist / 55, 0.07, 0.28) * aggressiveness
		local toMe = FlatUnit(root.Position - pos)
		if toMe then
			local approach = vel.X * toMe.X + vel.Z * toMe.Z
			if approach > 8 then
				adaptiveLead = adaptiveLead * 0.78
			end
		end
	end

	if Settings.PredictMode == "Intercept" then
		local mySpd = math.max(Settings.TV2_MySpeed or 18, 8)
		local closing = mySpd

		local rel = FlatUnit(pos - root.Position)
		if rel then
			local rivalAlong = vel.X * rel.X + vel.Z * rel.Z
			closing = math.max(mySpd - rivalAlong, 3.5)
		end

		local t
		if Settings.TV2_Enabled then
			local rivalSpeed = Vector3.new(vel.X, 0, vel.Z).Magnitude
			local closingSpeed = mySpd - rivalSpeed * 0.55
			t = flatDist / math.max(closingSpeed, 2.5)
			t = math.clamp(t, 0.04, 0.70) * aggressiveness
			t = math.clamp(t, 0.04, 0.72)

			if #Runtime.accelHistory > 0 then
				local avgAccel = Vector3.zero
				for _, a in ipairs(Runtime.accelHistory) do
					avgAccel = avgAccel + a
				end
				avgAccel = avgAccel / #Runtime.accelHistory
				return pos + vel * t + avgAccel * (t * t * 0.42)
			end
		else
			t = math.clamp(flatDist / closing, 0.05, 0.55) * aggressiveness
			t = math.clamp(t, 0.05, 0.55)
		end

		return pos + vel * t
	end

	local prediction = pos + vel * adaptiveLead

	if Settings.TV2_Enabled and #Runtime.accelHistory > 0 then
		local avgAccel = Vector3.zero
		for _, a in ipairs(Runtime.accelHistory) do
			avgAccel = avgAccel + a
		end
		avgAccel = avgAccel / #Runtime.accelHistory
		prediction = prediction + avgAccel * (adaptiveLead * adaptiveLead * 0.38)
	end

	return prediction
end


-- ============================================================================
-- [COMBAT POS]  Posición ideal, fases de pelea, destino de chase
-- ============================================================================

local function GetIdealFightPos(root, tRoot)
	local center, centerFlat = GetCachedCenter()
	local pred = PredictTargetPos(root, tRoot)
	local outward = FlatUnit(pred - centerFlat)
	local orbit = Settings.OrbitDistance or 6

	if Settings.TV2_Enabled and Runtime.combatPhase == "strike" then
		orbit = math.max(orbit * 0.65, 3.5)
	elseif Settings.TV2_Enabled and Runtime.combatPhase == "engage" then
		orbit = orbit * 0.85
	end

	if not outward then
		local toMe = FlatUnit(root.Position - pred)
		if not toMe then
			return pred + Vector3.new(orbit, 0, 0)
		end
		return pred + toMe * orbit
	end

	local ideal = pred - outward * orbit

	-- CenterBias más fuerte cerca de bordes (mejora segura)
	local _, myEdgeDist = GetNearestEdge(root.Position)
	local edgeFactor = 1
	if myEdgeDist < (Settings.EdgeDangerDist or 14) + 8 then
		edgeFactor = 1.35
	end

	if Settings.CenterBias then
		local toCenter = FlatUnit(centerFlat - ideal)
		if toCenter then
			ideal = ideal + toCenter * ((Settings.CenterBiasStrength or 0.55) * 4.5 * edgeFactor)
		end
	end

	if Settings.TV2_Enabled then
		local relVel = tRoot.AssemblyLinearVelocity - root.AssemblyLinearVelocity
		local relSpeed = Vector3.new(relVel.X, 0, relVel.Z).Magnitude
		if relSpeed > 4 then
			local dirRel = FlatUnit(relVel)
			if dirRel then
				ideal = ideal + dirRel * math.clamp(relSpeed * 0.07, 0, 2.6)
			end
		end
	end

	if Settings.TV2_Enabled and Settings.TV2_AngularApproach then
		local side = Vector3.new(-outward.Z, 0, outward.X)
		local lat = Settings.TV2_LateralOffset or 3.5
		local angle = Runtime.StrafeAngle % (math.pi * 2)
		local sideSign = math.cos(angle)
		ideal = ideal + side * (lat * sideSign)

		local realDist = FlatDist(root.Position, pred)
		if realDist < orbit * 0.6 then
			ideal = ideal + outward * (orbit * 0.35)
		elseif realDist > orbit * 1.35 then
			ideal = ideal - outward * (orbit * 0.28)
		end
	end

	if Settings.StrafeEnabled then
		local side = Vector3.new(-outward.Z, 0, outward.X)
		local strafeAmount = math.sin(Runtime.StrafeAngle) * 3.2
		local jitter = 0
		if Settings.OrbitJitter then
			local distFactor = math.clamp(FlatDist(root.Position, pred) / 18, 0, 1)
			jitter = math.sin(Runtime.orbitJitterPhase) * 1.7 * distFactor
		end
		ideal = ideal + side * (strafeAmount + jitter)

		if Settings.TV2_Enabled then
			local rivalDir = FlatUnit(tRoot.AssemblyLinearVelocity)
			if rivalDir then
				local dot = side:Dot(rivalDir)
				if dot > 0.45 then
					ideal = ideal + side * 2.3
				end
			end
		end
	end

	return Vector3.new(ideal.X, pred.Y, ideal.Z)
end

local function GetChaseDestination(root, tRoot)
	if Settings.CenterBias or (Settings.TV2_Enabled and Settings.TV2_AngularApproach) then
		return GetIdealFightPos(root, tRoot)
	end
	return PredictTargetPos(root, tRoot)
end

local function UpdateCombatPhase(dist)
	if not (Settings.TV2_Enabled and Settings.TV2_Spacing) then
		Runtime.combatPhase = "chase"
		return
	end
	if tick() < Runtime.justSlappedUntil then
		Runtime.combatPhase = "backoff"
		return
	end

	local strikeDist = Settings.TV2_StrikeDist or Settings.SlapRadius or 11
	local engageDist = Settings.TV2_EngageDist or 14

	if dist <= strikeDist then
		Runtime.combatPhase = "strike"
	elseif dist <= engageDist then
		Runtime.combatPhase = "engage"
	else
		Runtime.combatPhase = "chase"
	end
end

local function ShouldAvoidChase(root, tRoot)
	if not Settings.NoChaseIntoEdge then return false end
	local center, centerFlat = GetCachedCenter()
	local myOut  = FlatDist(root.Position, centerFlat)
	local hisOut = FlatDist(tRoot.Position, centerFlat)
	local _, myEdge = GetNearestEdge(root.Position)
	if hisOut > myOut and myEdge < (Settings.EdgeDangerDist or 14) + 10 then
		return true
	end
	return false
end

local function WantSlapDirecta()
	if Settings.FakeFlick then return true end
	if Settings.TV2_Enabled and Settings.TV2_SlapDirecta then return true end
	return false
end

-- Reset completo del slap SM (seguro)
local function ResetSlapState()
	Runtime.slapState = nil
	Runtime.slapStateT = 0
	Runtime.slapWantJump = false
	Runtime.cachedFlingDir = nil
end


-- ============================================================================
-- [ABILITY]  Disparo de habilidad (tecla E)
-- ============================================================================

local function TriggerAbility()
	if tick() - Runtime.LastAbilityTick < (Settings.AbilityCooldown or 1) then return end
	local char = GetChar()
	if not char or not char:FindFirstChildOfClass("Tool") then return end
	VirtualInputManager:SendKeyEvent(true, Enum.KeyCode.E, false, game)
	task.wait(0.04)
	VirtualInputManager:SendKeyEvent(false, Enum.KeyCode.E, false, game)
	Runtime.LastAbilityTick = tick()
end


-- ============================================================================
-- [SERVER HOP]
-- ============================================================================

local function ServerHop()
	SaveSettings()
	local placeId = GameIDs[Settings.ServerHopMode] or GameIDs.Main
	local hopped  = false
	pcall(function()
		local url  = ("https://games.roblox.com/v1/games/%d/servers/Public?sortOrder=Asc&limit=100"):format(placeId)
		local data = HttpService:JSONDecode(game:HttpGet(url))
		if data and data.data then
			for _, s in ipairs(data.data) do
				if s.playing and s.id ~= game.JobId and s.playing >= (Settings.MinPlayers or 0) then
					if not Settings.IgnoreLowPlayers or s.playing > 3 then
						TeleportService:TeleportToPlaceInstance(placeId, s.id, Player)
						hopped = true
						break
					end
				end
			end
		end
	end)
	if not hopped then
		TeleportService:Teleport(placeId, Player)
	end
end


-- ============================================================================
-- [TOURNAMENT]  Auto accept / decline del prompt de torneo
-- ============================================================================

local TournamentResponse = ReplicatedStorage:WaitForChild("Events"):WaitForChild("Tournament"):WaitForChild("TournamentResponse")
local SendPopup = ReplicatedStorage:WaitForChild("Events"):WaitForChild("Popups"):WaitForChild("SendPopup")

SendPopup.OnClientEvent:Connect(function(popupType)
	if popupType == "_tournamentRequest" and Settings.AutoTournament then
		TournamentResponse:FireServer(true)
		if StatusLabel then
			StatusLabel.Text = "Status: Tournament ACCEPT"
		end
	end
end)

local function HandleTournamentGUI()
	-- El RemoteEvent maneja el torneo al instante de forma pasiva
end


-- ============================================================================
-- [UI]  Interfaz completa (tabs, toggles, sliders, listas)
-- ============================================================================

local ScreenGui = Instance.new("ScreenGui")
ScreenGui.Name = "SlapMacroTryhardV91"
ScreenGui.ResetOnSpawn = false
ScreenGui.Parent = game:GetService("CoreGui")

local MainFrame = Instance.new("Frame")
MainFrame.Size = UDim2.new(0, 540, 0, 430)
MainFrame.Position = UDim2.new(0.5, -270, 0.5, -215)
MainFrame.BackgroundColor3 = Color3.fromRGB(16, 16, 20)
MainFrame.BorderSizePixel = 0
MainFrame.Active = true
MainFrame.Draggable = true
MainFrame.Parent = ScreenGui
Instance.new("UICorner", MainFrame).CornerRadius = UDim.new(0, 10)

local Title = Instance.new("TextLabel", MainFrame)
Title.Size = UDim2.new(1, -80, 0, 34)
Title.Position = UDim2.new(0, 12, 0, 4)
Title.BackgroundTransparency = 1
Title.Text = "Slap Macro  ·  Tryhard v9.1 Safe"
Title.TextColor3 = Color3.fromRGB(235, 235, 245)
Title.Font = Enum.Font.GothamBold
Title.TextSize = 16
Title.TextXAlignment = Enum.TextXAlignment.Left

local Sidebar = Instance.new("Frame", MainFrame)
Sidebar.Size = UDim2.new(0, 112, 1, -46)
Sidebar.Position = UDim2.new(0, 8, 0, 38)
Sidebar.BackgroundColor3 = Color3.fromRGB(26, 26, 32)
Sidebar.BorderSizePixel = 0
Instance.new("UICorner", Sidebar).CornerRadius = UDim.new(0, 8)
local SideList = Instance.new("UIListLayout", Sidebar)
SideList.Padding = UDim.new(0, 4)
local SidePad = Instance.new("UIPadding", Sidebar)
SidePad.PaddingTop   = UDim.new(0, 6)
SidePad.PaddingLeft  = UDim.new(0, 6)
SidePad.PaddingRight = UDim.new(0, 6)

local Container = Instance.new("Frame", MainFrame)
Container.Size = UDim2.new(1, -136, 1, -52)
Container.Position = UDim2.new(0, 128, 0, 42)
Container.BackgroundTransparency = 1

local TabFrames = {}

local function CreateTab(name)
	local btn = Instance.new("TextButton", Sidebar)
	btn.Size = UDim2.new(1, 0, 0, 28)
	btn.Text = name
	btn.BackgroundColor3 = Color3.fromRGB(38, 38, 46)
	btn.TextColor3 = Color3.fromRGB(220, 220, 230)
	btn.Font = Enum.Font.Gotham
	btn.TextSize = 12
	btn.BorderSizePixel = 0
	Instance.new("UICorner", btn).CornerRadius = UDim.new(0, 6)

	local page = Instance.new("ScrollingFrame", Container)
	page.Size = UDim2.new(1, 0, 1, 0)
	page.Visible = false
	page.BackgroundTransparency = 1
	page.BorderSizePixel = 0
	page.ScrollBarThickness = 3
	page.CanvasSize = UDim2.new(0, 0, 0, 0)
	local list = Instance.new("UIListLayout", page)
	list.Padding = UDim.new(0, 7)
	list:GetPropertyChangedSignal("AbsoluteContentSize"):Connect(function()
		page.CanvasSize = UDim2.new(0, 0, 0, list.AbsoluteContentSize.Y + 14)
	end)
	btn.MouseButton1Click:Connect(function()
		for _, v in pairs(TabFrames) do
			v.Visible = false
		end
		page.Visible = true
	end)
	TabFrames[name] = page
	return page
end

local function AddSlider(parent, label, key, min, max, step)
	step = step or 1
	local val = tonumber(Settings[key]) or min
	Settings[key] = val

	local f = Instance.new("Frame", parent)
	f.Size = UDim2.new(1, -6, 0, 42)
	f.BackgroundTransparency = 1

	local titleLbl = Instance.new("TextLabel", f)
	titleLbl.Size = UDim2.new(0.6, 0, 0, 16)
	titleLbl.BackgroundTransparency = 1
	titleLbl.TextColor3 = Color3.fromRGB(220, 220, 230)
	titleLbl.Font = Enum.Font.Gotham
	titleLbl.TextSize = 12
	titleLbl.TextXAlignment = Enum.TextXAlignment.Left
	titleLbl.Text = label .. ":"

	local box = Instance.new("TextBox", f)
	box.Size = UDim2.new(0.38, 0, 0, 16)
	box.Position = UDim2.new(0.62, 0, 0, 0)
	box.BackgroundColor3 = Color3.fromRGB(35, 35, 45)
	box.TextColor3 = Color3.fromRGB(0, 220, 130)
	box.Font = Enum.Font.GothamBold
	box.TextSize = 12
	box.TextXAlignment = Enum.TextXAlignment.Right
	box.Text = tostring(val)
	box.ClearTextOnFocus = false
	Instance.new("UICorner", box).CornerRadius = UDim.new(0, 4)

	local function makeBtn(text, xScale, xOffset)
		local b = Instance.new("TextButton", f)
		b.Size = UDim2.new(0, 32, 0, 20)
		b.Position = UDim2.new(xScale, xOffset, 0, 18)
		b.Text = text
		b.BackgroundColor3 = Color3.fromRGB(50, 50, 60)
		b.TextColor3 = Color3.new(1, 1, 1)
		b.BorderSizePixel = 0
		Instance.new("UICorner", b).CornerRadius = UDim.new(0, 5)
		return b
	end

	local mi = makeBtn("-", 0, 0)
	local pl = makeBtn("+", 1, -32)

	local function apply(n)
		val = math.floor((tonumber(n) or val) / step + 0.5) * step
		if step >= 1 then val = math.floor(val + 0.001) end
		Settings[key] = val
		box.Text = tostring(val)
		SaveSettings()
	end

	box.FocusLost:Connect(function()
		local num = tonumber(box.Text)
		if num then
			apply(num)
		else
			box.Text = tostring(val)
		end
	end)

	local function hold(btn, dir)
		local holding = false
		btn.MouseButton1Down:Connect(function()
			holding = true
			apply(val + dir * step)
			task.spawn(function()
				task.wait(0.35)
				while holding do
					apply(val + dir * step)
					task.wait(0.06)
				end
			end)
		end)
		btn.MouseButton1Up:Connect(function() holding = false end)
		btn.MouseLeave:Connect(function() holding = false end)
	end

	hold(mi, -1)
	hold(pl, 1)
end

local function AddToggle(parent, label, key)
	local f = Instance.new("Frame", parent)
	f.Size = UDim2.new(1, -6, 0, 26)
	f.BackgroundTransparency = 1
	local l = Instance.new("TextLabel", f)
	l.Size = UDim2.new(0.72, 0, 1, 0)
	l.BackgroundTransparency = 1
	l.TextColor3 = Color3.fromRGB(220, 220, 230)
	l.Font = Enum.Font.Gotham
	l.TextSize = 12
	l.TextXAlignment = Enum.TextXAlignment.Left
	l.Text = label
	local b = Instance.new("TextButton", f)
	b.Size = UDim2.new(0, 44, 0, 20)
	b.Position = UDim2.new(1, -46, 0.5, -10)
	b.Text = ""
	b.BorderSizePixel = 0
	Instance.new("UICorner", b).CornerRadius = UDim.new(0, 6)
	local function vis(v)
		b.BackgroundColor3 = v and Color3.fromRGB(0, 185, 105) or Color3.fromRGB(185, 50, 50)
	end
	vis(Settings[key])
	b.MouseButton1Click:Connect(function()
		Settings[key] = not Settings[key]
		vis(Settings[key])
		SaveSettings()
	end)
end

local function AddCycle(parent, label, key, options)
	local btn = Instance.new("TextButton", parent)
	btn.Size = UDim2.new(1, -6, 0, 28)
	btn.BackgroundColor3 = Color3.fromRGB(42, 42, 52)
	btn.TextColor3 = Color3.fromRGB(230, 230, 240)
	btn.Font = Enum.Font.Gotham
	btn.TextSize = 12
	btn.BorderSizePixel = 0
	btn.Text = label .. ": " .. tostring(Settings[key])
	Instance.new("UICorner", btn).CornerRadius = UDim.new(0, 6)
	btn.MouseButton1Click:Connect(function()
		local idx = table.find(options, Settings[key]) or 1
		Settings[key] = options[idx % #options + 1]
		btn.Text = label .. ": " .. tostring(Settings[key])
		SaveSettings()
	end)
end

-- Tabs
local MainTab      = CreateTab("Main")
local BotTab       = CreateTab("Bot")
local CombatTab    = CreateTab("Combat")
local TryhardTab   = CreateTab("Tryhard")
local TryhardV2Tab = CreateTab("TryhardV2")
local ListsTab     = CreateTab("Lists")
local ServerTab    = CreateTab("Server")

-- ----- MAIN -----
local StartBtn = Instance.new("TextButton", MainTab)
StartBtn.Size = UDim2.new(1, -6, 0, 46)
StartBtn.Text = Settings.Enabled and "STOP MACRO" or "START MACRO"
StartBtn.BackgroundColor3 = Settings.Enabled and Color3.fromRGB(175, 35, 45) or Color3.fromRGB(0, 165, 85)
StartBtn.TextColor3 = Color3.new(1, 1, 1)
StartBtn.Font = Enum.Font.GothamBold
StartBtn.TextSize = 15
StartBtn.BorderSizePixel = 0
Instance.new("UICorner", StartBtn).CornerRadius = UDim.new(0, 8)
StartBtn.MouseButton1Click:Connect(function()
	Settings.Enabled = not Settings.Enabled
	StartBtn.Text = Settings.Enabled and "STOP MACRO" or "START MACRO"
	StartBtn.BackgroundColor3 = Settings.Enabled and Color3.fromRGB(175, 35, 45) or Color3.fromRGB(0, 165, 85)
	Runtime.LastReachedTime = tick()
	ResetSlapState()
	Runtime.cachedFlingDir = nil
	SaveSettings()
end)

StatusLabel = Instance.new("TextLabel", MainTab)
StatusLabel.Size = UDim2.new(1, -6, 0, 42)
StatusLabel.BackgroundTransparency = 1
StatusLabel.TextColor3 = Color3.fromRGB(155, 155, 175)
StatusLabel.Font = Enum.Font.Gotham
StatusLabel.TextSize = 12
StatusLabel.TextXAlignment = Enum.TextXAlignment.Left
StatusLabel.TextYAlignment = Enum.TextYAlignment.Top
StatusLabel.TextWrapped = true
StatusLabel.Text = "Status: Idle"

DebugLabel = Instance.new("TextLabel", MainTab)
DebugLabel.Size = UDim2.new(1, -6, 0, 38)
DebugLabel.BackgroundTransparency = 1
DebugLabel.TextColor3 = Color3.fromRGB(120, 160, 200)
DebugLabel.Font = Enum.Font.Gotham
DebugLabel.TextSize = 11
DebugLabel.TextXAlignment = Enum.TextXAlignment.Left
DebugLabel.TextYAlignment = Enum.TextYAlignment.Top
DebugLabel.TextWrapped = true
DebugLabel.Text = ""
DebugLabel.Visible = false

AddCycle(MainTab, "Movement", "MovementMode", { "Full", "Legal" })
AddToggle(MainTab, "Pausar sin glove (permite otras tools)", "PauseWhenUnequipped")
AddToggle(MainTab, "Prefer Arena (entered)", "PreferArena")
AddToggle(MainTab, "Auto Tournament Accept", "AutoTournament")
AddToggle(MainTab, "Ignore Oneshots", "IgnoreOneshots")
AddToggle(MainTab, "Ignore Ragdoll", "IgnoreRagdoll")
AddToggle(MainTab, "Ignore Spectator", "IgnoreSpectator")
AddToggle(MainTab, "Debug Mode", "DebugMode")

-- ----- BOT -----
AddCycle(BotTab, "Bot Type", "BotType", { "Tryhard", "Closest", "LowestHP", "Furthest", "Random" })
AddSlider(BotTab, "Slap Radius", "SlapRadius", 5, 25, 1)
AddSlider(BotTab, "Engage Dist", "EngageRadius", 2, 15, 1)
AddToggle(BotTab, "BackOff After Slap", "BackOffAfterSlap")
AddSlider(BotTab, "BackOff Dist", "BackOffDistance", 4, 15, 1)
AddToggle(BotTab, "Strafe / Orbit", "StrafeEnabled")
AddSlider(BotTab, "Strafe Radius", "StrafeRadius", 6, 40, 1)
AddSlider(BotTab, "Orbit Distance", "OrbitDistance", 3, 12, 1)
AddSlider(BotTab, "Strafe Speed", "StrafeSpeed", 1, 9, 0.5)
AddToggle(BotTab, "Orbit Jitter", "OrbitJitter")
AddToggle(BotTab, "Jumps", "Jumps")
AddSlider(BotTab, "Jump Chance %", "JumpChance", 1, 100, 1)
AddToggle(BotTab, "Jump On Engage", "JumpOnEngage")

-- ----- COMBAT -----
AddToggle(CombatTab, "Anti Counter (Anim)", "AntiCounter")
AddToggle(CombatTab, "Anti Megarock", "AntiMegarock")
AddSlider(CombatTab, "Megarock Safe Dist", "MegarockSafeDist", 8, 28, 1)
AddSlider(CombatTab, "Force Reequip Max Time", "ForceReequipMaxTime", 0.6, 2.5, 0.05)
AddToggle(CombatTab, "Always Face Target", "FaceTarget")
AddSlider(CombatTab, "Face Radius", "ShiftlockRadius", 5, 100, 1)
AddToggle(CombatTab, "Unequip When Far", "UnequipFar")
AddSlider(CombatTab, "Unequip Radius", "UnequipRadius", 10, 100, 1)
AddToggle(CombatTab, "Danger Mode", "DangerMode")
AddCycle(CombatTab, "Danger Type", "DangerType", { "Backoff", "Give up", "Ahh" })
AddSlider(CombatTab, "Danger HP", "DangerHP", 10, 90, 5)
AddToggle(CombatTab, "Auto Ability (E)", "AutoAbility")
AddCycle(CombatTab, "Ability Mode", "AbilityMode", { "Combat", "Defensive", "Instant", "Combo", "Camping" })
AddSlider(CombatTab, "Ability CD", "AbilityCooldown", 0.3, 5, 0.1)
AddSlider(CombatTab, "Stuck Timeout", "StuckTimeout", 15, 120, 5)
AddSlider(CombatTab, "Slap Cooldown", "SlapCooldown", 0.08, 0.4, 0.01)

-- ----- TRYHARD -----
AddToggle(TryhardTab, "Fake Flick (outward)", "FakeFlick")
AddSlider(TryhardTab, "Flick Aim Time", "FlickAimTime", 0.02, 0.12, 0.005)
AddSlider(TryhardTab, "Flick Lock Time", "FlickLockTime", 0.1, 0.4, 0.02)
AddSlider(TryhardTab, "Fling Outward Weight", "FlingOutwardWeight", 0.3, 1, 0.05)
AddToggle(TryhardTab, "Edge Safe (anti void)", "EdgeSafe")
AddSlider(TryhardTab, "Edge Danger Dist", "EdgeDangerDist", 6, 30, 1)
AddToggle(TryhardTab, "No Chase Into Edge", "NoChaseIntoEdge")
AddToggle(TryhardTab, "Center Bias", "CenterBias")
AddSlider(TryhardTab, "Center Bias Strength", "CenterBiasStrength", 0.1, 1, 0.05)
AddToggle(TryhardTab, "Predict Movement", "PredictMovement")
AddCycle(TryhardTab, "Predict Mode", "PredictMode", { "Simple", "Intercept" })
AddSlider(TryhardTab, "Predict Lead", "PredictLead", 0.05, 0.45, 0.01)
AddSlider(TryhardTab, "Predict Aggressiveness", "PredictAggressiveness", 0.5, 1.6, 0.05)
AddSlider(TryhardTab, "Min Slap Separation", "MinSlapSeparation", 1, 6, 0.5)

local Tip = Instance.new("TextLabel", TryhardTab)
Tip.Size = UDim2.new(1, -6, 0, 72)
Tip.BackgroundTransparency = 1
Tip.TextColor3 = Color3.fromRGB(140, 140, 160)
Tip.Font = Enum.Font.Gotham
Tip.TextSize = 11
Tip.TextXAlignment = Enum.TextXAlignment.Left
Tip.TextYAlignment = Enum.TextYAlignment.Top
Tip.TextWrapped = true
Tip.Text =
	"Predict Simple = pos+vel*lead.\nPredict Intercept = estima tiempo de encuentro.\nFake Flick = slap dirigida al void.\nTryhardV2 = spacing / angular / double slap.\nPredict Aggressiveness multiplica el lead (1.0 = normal)."

-- ----- TRYHARD V2 -----
AddToggle(TryhardV2Tab, "Enable Tryhard v2", "TV2_Enabled")
AddToggle(TryhardV2Tab, "Spacing (Chase→Engage→Strike→Backoff)", "TV2_Spacing")
AddSlider(TryhardV2Tab, "Engage Dist", "TV2_EngageDist", 8, 25, 1)
AddSlider(TryhardV2Tab, "Strike Dist", "TV2_StrikeDist", 5, 18, 1)
AddToggle(TryhardV2Tab, "Angular Approach (lado)", "TV2_AngularApproach")
AddSlider(TryhardV2Tab, "Lateral Offset", "TV2_LateralOffset", 1, 10, 0.5)
AddToggle(TryhardV2Tab, "Slap Directa (orient fling)", "TV2_SlapDirecta")
AddToggle(TryhardV2Tab, "Double Slap (jump mid)", "TV2_DoubleSlap")
AddSlider(TryhardV2Tab, "Double Slap Delay", "TV2_DoubleSlapDelay", 0.06, 0.3, 0.01)
AddToggle(TryhardV2Tab, "Velocity Smooth", "TV2_VelocitySmooth")
AddSlider(TryhardV2Tab, "My Speed (intercept)", "TV2_MySpeed", 10, 28, 1)

local Tip2 = Instance.new("TextLabel", TryhardV2Tab)
Tip2.Size = UDim2.new(1, -6, 0, 96)
Tip2.BackgroundTransparency = 1
Tip2.TextColor3 = Color3.fromRGB(140, 140, 160)
Tip2.Font = Enum.Font.Gotham
Tip2.TextSize = 11
Tip2.TextXAlignment = Enum.TextXAlignment.Left
Tip2.TextYAlignment = Enum.TextYAlignment.Top
Tip2.TextWrapped = true
Tip2.Text =
	"Universal (sin perfiles por guante).\nSpacing: estados de pelea claros.\nAngular: entra de lado, menos trades.\nSlap Directa: misma idea que flick outward.\nDouble Slap: Activate → jump → 2º Activate."

-- ----- LISTS (Focus / Whitelist con avatares + búsqueda parcial) -----

local function GetAvatarUrl(userId)
	if not userId or userId <= 0 then
		return "rbxasset://textures/ui/GuiImagePlaceholder.png"
	end
	return ("rbxthumb://type=AvatarHeadShot&id=%d&w=48&h=48"):format(userId)
end

local function FindPlayerByPartial(partial)
	partial = string.lower(tostring(partial or ""))
	if partial == "" then return nil end
	for _, p in ipairs(Players:GetPlayers()) do
		if string.lower(p.Name) == partial then return p end
	end
	for _, p in ipairs(Players:GetPlayers()) do
		if string.find(string.lower(p.Name), partial, 1, true) then return p end
	end
	for _, p in ipairs(Players:GetPlayers()) do
		if p.DisplayName and string.find(string.lower(p.DisplayName), partial, 1, true) then
			return p
		end
	end
	return nil
end

local function GetOnlineMatches(partial, maxN)
	partial = string.lower(tostring(partial or ""))
	maxN = maxN or 6
	local matches = {}
	if partial == "" then return matches end
	for _, p in ipairs(Players:GetPlayers()) do
		if p ~= Player then
			local n = string.lower(p.Name)
			local d = p.DisplayName and string.lower(p.DisplayName) or ""
			if n == partial or d == partial
				or string.find(n, partial, 1, true)
				or string.find(d, partial, 1, true) then
				table.insert(matches, p)
				if #matches >= maxN then break end
			end
		end
	end
	return matches
end

local function CreatePlayerBubbleRow(parent, name, accentColor, onRemove)
	local row = Instance.new("Frame", parent)
	row.Size = UDim2.new(1, -4, 0, 32)
	row.BackgroundColor3 = Color3.fromRGB(32, 32, 40)
	row.BorderSizePixel = 0
	Instance.new("UICorner", row).CornerRadius = UDim.new(0, 8)

	local bubble = Instance.new("ImageLabel", row)
	bubble.Size = UDim2.new(0, 26, 0, 26)
	bubble.Position = UDim2.new(0, 4, 0.5, -13)
	bubble.BackgroundColor3 = Color3.fromRGB(50, 50, 60)
	bubble.BorderSizePixel = 0
	bubble.ScaleType = Enum.ScaleType.Crop
	Instance.new("UICorner", bubble).CornerRadius = UDim.new(1, 0)

	local userId = 0
	local online = Players:FindFirstChild(name)
	if online then
		userId = online.UserId
	else
		local p = FindPlayerByPartial(name)
		if p then userId = p.UserId end
	end
	bubble.Image = GetAvatarUrl(userId)

	local nameLbl = Instance.new("TextLabel", row)
	nameLbl.Size = UDim2.new(1, -70, 1, 0)
	nameLbl.Position = UDim2.new(0, 36, 0, 0)
	nameLbl.BackgroundTransparency = 1
	nameLbl.Text = name
	nameLbl.TextColor3 = accentColor
	nameLbl.Font = Enum.Font.Gotham
	nameLbl.TextSize = 12
	nameLbl.TextXAlignment = Enum.TextXAlignment.Left
	nameLbl.TextTruncate = Enum.TextTruncate.AtEnd

	local rm = Instance.new("TextButton", row)
	rm.Size = UDim2.new(0, 26, 0, 22)
	rm.Position = UDim2.new(1, -30, 0.5, -11)
	rm.Text = "×"
	rm.BackgroundColor3 = Color3.fromRGB(120, 40, 45)
	rm.TextColor3 = Color3.new(1, 1, 1)
	rm.Font = Enum.Font.GothamBold
	rm.TextSize = 16
	rm.BorderSizePixel = 0
	Instance.new("UICorner", rm).CornerRadius = UDim.new(0, 6)
	rm.MouseButton1Click:Connect(onRemove)

	return row
end

local function CreateListSection(parent, title, listKey, accentColor)
	local searchFrame = Instance.new("Frame", parent)
	searchFrame.Size = UDim2.new(1, -6, 0, 32)
	searchFrame.BackgroundTransparency = 1

	local box = Instance.new("TextBox", searchFrame)
	box.Size = UDim2.new(1, -70, 1, 0)
	box.BackgroundColor3 = Color3.fromRGB(35, 35, 45)
	box.TextColor3 = Color3.new(1, 1, 1)
	box.PlaceholderText = "Escribe parte del nombre..."
	box.Text = ""
	box.Font = Enum.Font.Gotham
	box.TextSize = 12
	box.ClearTextOnFocus = false
	Instance.new("UICorner", box).CornerRadius = UDim.new(0, 6)

	local addBtn = Instance.new("TextButton", searchFrame)
	addBtn.Size = UDim2.new(0, 62, 1, 0)
	addBtn.Position = UDim2.new(1, -62, 0, 0)
	addBtn.Text = "Add"
	addBtn.BackgroundColor3 = Color3.fromRGB(0, 130, 90)
	addBtn.TextColor3 = Color3.new(1, 1, 1)
	addBtn.Font = Enum.Font.GothamBold
	addBtn.TextSize = 12
	addBtn.BorderSizePixel = 0
	Instance.new("UICorner", addBtn).CornerRadius = UDim.new(0, 6)

	local suggestFrame = Instance.new("Frame", parent)
	suggestFrame.Size = UDim2.new(1, -6, 0, 0)
	suggestFrame.BackgroundColor3 = Color3.fromRGB(28, 28, 36)
	suggestFrame.BorderSizePixel = 0
	suggestFrame.Visible = false
	suggestFrame.ClipsDescendants = true
	Instance.new("UICorner", suggestFrame).CornerRadius = UDim.new(0, 6)
	local suggestList = Instance.new("UIListLayout", suggestFrame)
	suggestList.Padding = UDim.new(0, 2)
	local suggestPad = Instance.new("UIPadding", suggestFrame)
	suggestPad.PaddingTop = UDim.new(0, 3)
	suggestPad.PaddingBottom = UDim.new(0, 3)
	suggestPad.PaddingLeft = UDim.new(0, 4)
	suggestPad.PaddingRight = UDim.new(0, 4)

	local bubblesFrame = Instance.new("Frame", parent)
	bubblesFrame.Size = UDim2.new(1, -6, 0, 0)
	bubblesFrame.BackgroundTransparency = 1
	local bubblesList = Instance.new("UIListLayout", bubblesFrame)
	bubblesList.Padding = UDim.new(0, 4)
	bubblesList:GetPropertyChangedSignal("AbsoluteContentSize"):Connect(function()
		bubblesFrame.Size = UDim2.new(1, -6, 0, math.max(bubblesList.AbsoluteContentSize.Y, 4))
	end)

	local function refreshBubbles()
		for _, c in ipairs(bubblesFrame:GetChildren()) do
			if not c:IsA("UIListLayout") then c:Destroy() end
		end
		local list = Settings[listKey] or {}
		if #list == 0 then
			local empty = Instance.new("TextLabel", bubblesFrame)
			empty.Size = UDim2.new(1, 0, 0, 22)
			empty.BackgroundTransparency = 1
			empty.Text = "(" .. title .. " vacío)"
			empty.TextColor3 = Color3.fromRGB(120, 120, 140)
			empty.Font = Enum.Font.Gotham
			empty.TextSize = 11
			empty.TextXAlignment = Enum.TextXAlignment.Left
			return
		end
		for i, name in ipairs(list) do
			CreatePlayerBubbleRow(bubblesFrame, name, accentColor, function()
				table.remove(Settings[listKey], i)
				refreshBubbles()
				SaveSettings()
			end)
		end
	end

	local function clearSuggestions()
		for _, c in ipairs(suggestFrame:GetChildren()) do
			if not c:IsA("UIListLayout") and not c:IsA("UIPadding") then c:Destroy() end
		end
		suggestFrame.Visible = false
		suggestFrame.Size = UDim2.new(1, -6, 0, 0)
	end

	local function showSuggestions(partial)
		clearSuggestions()
		local matches = GetOnlineMatches(partial, 5)
		if #matches == 0 then return end
		suggestFrame.Visible = true
		local h = 0
		for _, p in ipairs(matches) do
			local row = Instance.new("TextButton", suggestFrame)
			row.Size = UDim2.new(1, -4, 0, 28)
			row.BackgroundColor3 = Color3.fromRGB(40, 40, 52)
			row.Text = ""
			row.BorderSizePixel = 0
			Instance.new("UICorner", row).CornerRadius = UDim.new(0, 6)

			local av = Instance.new("ImageLabel", row)
			av.Size = UDim2.new(0, 22, 0, 22)
			av.Position = UDim2.new(0, 4, 0.5, -11)
			av.BackgroundColor3 = Color3.fromRGB(50, 50, 60)
			av.BorderSizePixel = 0
			av.Image = GetAvatarUrl(p.UserId)
			av.ScaleType = Enum.ScaleType.Crop
			Instance.new("UICorner", av).CornerRadius = UDim.new(1, 0)

			local lbl = Instance.new("TextLabel", row)
			lbl.Size = UDim2.new(1, -34, 1, 0)
			lbl.Position = UDim2.new(0, 30, 0, 0)
			lbl.BackgroundTransparency = 1
			lbl.Text = p.Name
			lbl.TextColor3 = Color3.fromRGB(230, 230, 240)
			lbl.Font = Enum.Font.Gotham
			lbl.TextSize = 12
			lbl.TextXAlignment = Enum.TextXAlignment.Left

			row.MouseButton1Click:Connect(function()
				local list = Settings[listKey]
				local already = false
				for _, n in ipairs(list) do
					if string.lower(n) == string.lower(p.Name) then already = true break end
				end
				if not already then
					table.insert(list, p.Name)
					refreshBubbles()
					SaveSettings()
				end
				box.Text = ""
				clearSuggestions()
			end)
			h = h + 30
		end
		suggestFrame.Size = UDim2.new(1, -6, 0, h + 6)
	end

	local function tryAdd(text)
		text = string.gsub(tostring(text or ""), "^%s*(.-)%s*$", "%1")
		if text == "" then return end
		local p = FindPlayerByPartial(text)
		local finalName = p and p.Name or text
		local list = Settings[listKey]
		for _, n in ipairs(list) do
			if string.lower(n) == string.lower(finalName) then return end
		end
		table.insert(list, finalName)
		refreshBubbles()
		SaveSettings()
		box.Text = ""
		clearSuggestions()
	end

	addBtn.MouseButton1Click:Connect(function() tryAdd(box.Text) end)
	box.FocusLost:Connect(function(enter)
		if enter then tryAdd(box.Text) end
	end)
	box:GetPropertyChangedSignal("Text"):Connect(function()
		if #box.Text >= 1 then
			showSuggestions(box.Text)
		else
			clearSuggestions()
		end
	end)

	local clearBtn = Instance.new("TextButton", parent)
	clearBtn.Size = UDim2.new(1, -6, 0, 26)
	clearBtn.Text = "Clear " .. title
	clearBtn.BackgroundColor3 = Color3.fromRGB(90, 50, 50)
	clearBtn.TextColor3 = Color3.new(1, 1, 1)
	clearBtn.Font = Enum.Font.Gotham
	clearBtn.TextSize = 12
	clearBtn.BorderSizePixel = 0
	Instance.new("UICorner", clearBtn).CornerRadius = UDim.new(0, 6)
	clearBtn.MouseButton1Click:Connect(function()
		Settings[listKey] = {}
		refreshBubbles()
		SaveSettings()
	end)

	refreshBubbles()
end

AddToggle(ListsTab, "Focus Enabled", "FocusEnabled")
AddCycle(ListsTab, "Focus Mode", "FocusMode", { "Prefer", "Only" })

local sep1 = Instance.new("Frame", ListsTab)
sep1.Size = UDim2.new(1, -6, 0, 1)
sep1.BackgroundColor3 = Color3.fromRGB(50, 50, 65)
sep1.BorderSizePixel = 0

CreateListSection(ListsTab, "Focus", "FocusList", Color3.fromRGB(160, 190, 255))

local sep2 = Instance.new("Frame", ListsTab)
sep2.Size = UDim2.new(1, -6, 0, 8)
sep2.BackgroundTransparency = 1

local sep3 = Instance.new("Frame", ListsTab)
sep3.Size = UDim2.new(1, -6, 0, 1)
sep3.BackgroundColor3 = Color3.fromRGB(50, 50, 65)
sep3.BorderSizePixel = 0

AddToggle(ListsTab, "Whitelist Enabled", "WhitelistEnabled")
CreateListSection(ListsTab, "Whitelist", "Whitelist", Color3.fromRGB(160, 255, 190))

-- ----- SERVER -----
local HopNow = Instance.new("TextButton", ServerTab)
HopNow.Size = UDim2.new(1, -6, 0, 38)
HopNow.Text = "SERVER HOP NOW"
HopNow.BackgroundColor3 = Color3.fromRGB(0, 115, 195)
HopNow.TextColor3 = Color3.new(1, 1, 1)
HopNow.Font = Enum.Font.GothamBold
HopNow.BorderSizePixel = 0
Instance.new("UICorner", HopNow).CornerRadius = UDim.new(0, 8)
HopNow.MouseButton1Click:Connect(ServerHop)

AddCycle(ServerTab, "Hop Mode", "ServerHopMode", { "Main", "NoOneshots", "KS" })
AddSlider(ServerTab, "Min Players", "MinPlayers", 0, 14, 1)
AddToggle(ServerTab, "Ignore Very Low Pop", "IgnoreLowPlayers")
AddToggle(ServerTab, "Hop on Timer", "AutoHopTimerEnabled")
AddSlider(ServerTab, "Timer (min)", "AutoHopMinutes", 5, 180, 5)
AddToggle(ServerTab, "Hop on Oneshots", "HopOnOneshotsEnabled")
AddSlider(ServerTab, "Oneshot Limit", "MaxOneshotsAllowed", 1, 6, 1)

-- Minimize / Maximize
local Plus = Instance.new("TextButton", ScreenGui)
Plus.Size = UDim2.new(0, 40, 0, 40)
Plus.Position = UDim2.new(0.04, 0, 0.08, 0)
Plus.Text = "+"
Plus.Visible = false
Plus.BackgroundColor3 = Color3.fromRGB(0, 165, 85)
Plus.TextColor3 = Color3.new(1, 1, 1)
Plus.Font = Enum.Font.GothamBold
Plus.TextSize = 20
Plus.BorderSizePixel = 0
Instance.new("UICorner", Plus).CornerRadius = UDim.new(1, 0)

local Minus = Instance.new("TextButton", MainFrame)
Minus.Size = UDim2.new(0, 28, 0, 28)
Minus.Position = UDim2.new(1, -34, 0, 4)
Minus.Text = "–"
Minus.BackgroundTransparency = 1
Minus.TextColor3 = Color3.fromRGB(200, 200, 210)
Minus.Font = Enum.Font.GothamBold
Minus.TextSize = 20
Minus.MouseButton1Click:Connect(function()
	MainFrame.Visible = false
	Plus.Visible = true
end)
Plus.MouseButton1Click:Connect(function()
	MainFrame.Visible = true
	Plus.Visible = false
end)

MainTab.Visible = true


-- ============================================================================
-- [MAIN LOOP]  Heartbeat – movimiento, slap SM, anti-counter, tools
-- ============================================================================

RunService.Heartbeat:Connect(function(dt)
	HandleTournamentGUI()

	if not Settings.Enabled then
		Runtime.LastReachedTime = tick()
		StatusLabel.Text = "Status: Idle"
		if DebugLabel then DebugLabel.Visible = false end
		ResetSlapState()
		return
	end

	local char = GetChar()
	local hum  = GetHum(char)
	local root = GetRoot(char)
	if not root or not hum or hum.Health <= 0 then
		ResetSlapState()
		return
	end

	-- --- Detección de tools ---
	local gloveName         = GetGloveName()
	local gloveTool         = nil
	local gloveEquipped     = false
	local otherToolEquipped = false
	local equippedTool      = nil

	for _, item in ipairs(char:GetChildren()) do
		if item:IsA("Tool") then
			equippedTool = item
			if gloveName and item.Name == gloveName then
				gloveTool = item
				gloveEquipped = true
			else
				otherToolEquipped = true
			end
		end
	end
	if not gloveTool and gloveName then
		gloveTool = Player.Backpack:FindFirstChild(gloveName)
	end

	-- ¿Prompt de torneo abierto?
	local tourneyOpen = false
	local component = PlayerGui:FindFirstChild("Component")
	if component then
		local slapT = component:FindFirstChild("SlapTournament")
		local intermission = component:FindFirstChild("Intermission")
		if (slapT and (slapT.Visible or slapT.AbsoluteSize.X > 1)) or
		   (intermission and (intermission.Visible or intermission.AbsoluteSize.X > 1)) then
			tourneyOpen = true
		end
	end

	-- Sin guante → lobby / idle
	if not gloveTool then
		ResetSlapState()
		Runtime.forceReequip = false
		if tourneyOpen and Settings.AutoTournament then
			StatusLabel.Text = "Status: Tournament UI"
			return
		end
		if Settings.MovementMode ~= "Legal" then
			local tp = workspace:FindFirstChild("Lobby") and workspace.Lobby:FindFirstChild("Teleport1")
			if tp then
				MoveToTarget(tp.Position)
				StatusLabel.Text = "Status: Going to Portal (Lobby)"
			end
		else
			StatusLabel.Text = "Status: Legal | sin guante"
		end
		Runtime.LastReachedTime = tick()
		return
	end

	local target = GetTarget()

	-- Reset predicción al cambiar de target (mejora segura)
	if target ~= Runtime.lastTarget then
		ResetPrediction()
		Runtime.lastTarget = target
	end

	-- Anti Counter: unequip + back off + límite de tiempo
	if gloveEquipped and target and TargetIsCountering(target) then
		ResetSlapState()
		Runtime.forceReequip = true
		Runtime.forceReequipStart = tick()
		hum:UnequipTools()
		StatusLabel.Text = "Status: Anti-Counter! (Guante desequipado)"
		if Settings.MovementMode ~= "Legal" then
			local tRootCounter = GetRoot(target.Character)
			if tRootCounter then
				local away = FlatUnit(root.Position - tRootCounter.Position)
				if away then
					MoveToTarget(root.Position + away * 13)
				end
			end
		end
		return
	end

	-- Límite de tiempo de forceReequip (evita quedarse unequipped)
	if Runtime.forceReequip and tick() - Runtime.forceReequipStart > (Settings.ForceReequipMaxTime or 1.25) then
		Runtime.forceReequip = false
	end

	-- --- Política de tools (inteligente y segura) ---
	-- 1) Nunca quitar una tool que NO sea el glove.
	-- 2) Lógica de combate SOLO si el glove está equipado.
	-- 3) forceReequip (anti-counter) sí fuerza el glove (con timeout).
	-- 4) Si PauseWhenUnequipped=OFF y no hay otra tool, reequipa el glove.
	if not gloveEquipped then
		if Runtime.forceReequip then
			if otherToolEquipped then
				hum:UnequipTools()
			end
			if gloveTool.Parent == Player.Backpack then
				hum:EquipTool(gloveTool)
			end
			Runtime.forceReequip = false
			StatusLabel.Text = "Status: Reequipando guante..."
			Runtime.LastReachedTime = tick()
			return
		end

		if Settings.PauseWhenUnequipped then
			ResetSlapState()
			if otherToolEquipped then
				StatusLabel.Text = "Status: PAUSADO — otra tool equipada ("
					.. (equippedTool and equippedTool.Name or "?") .. ")"
			else
				StatusLabel.Text = "Status: PAUSADO — equípate el guante"
			end
			Runtime.LastReachedTime = tick()
			return
		end

		if not otherToolEquipped and gloveTool.Parent == Player.Backpack then
			hum:EquipTool(gloveTool)
			StatusLabel.Text = "Status: Equipando guante..."
			Runtime.LastReachedTime = tick()
			return
		end

		ResetSlapState()
		StatusLabel.Text = "Status: Otra tool en mano — lógica pausada"
		Runtime.LastReachedTime = tick()
		return
	end

	-- A partir de aquí: glove EQUIPPADO → lógica activa
	Runtime.forceReequip = false

	-- Hop por oneshots / timer / jugadores bajos
	if Settings.HopOnOneshotsEnabled then
		local n = 0
		for _, p in ipairs(Players:GetPlayers()) do
			if p ~= Player and HasOneShot(p) then n += 1 end
		end
		if n >= Settings.MaxOneshotsAllowed then
			StatusLabel.Text = "Status: Hop (Oneshots)"
			ServerHop()
			return
		end
	end

	if Settings.AutoHopTimerEnabled then
		if tick() - (Settings.LastHopTick or 0) >= Settings.AutoHopMinutes * 60 then
			Settings.LastHopTick = tick()
			SaveSettings()
			ServerHop()
			return
		end
	end

	if Settings.MinPlayers > 0 and #Players:GetPlayers() < Settings.MinPlayers then
		if not Settings.IgnoreLowPlayers then
			StatusLabel.Text = "Status: Hop (Low players)"
			ServerHop()
			return
		end
	end

	if not target or not target.Character then
		Runtime.LastReachedTime = tick()
		ResetSlapState()
		StatusLabel.Text = Settings.FocusMode == "Only" and "Status: No focus target" or "Status: No target"
		return
	end

	local tRoot = GetRoot(target.Character)
	if not tRoot then
		ResetSlapState()
		return
	end

	GetSmoothedVelocity(tRoot)

	local dist = (root.Position - tRoot.Position).Magnitude
	UpdateCombatPhase(dist)
	local _, edgeDist = GetNearestEdge(root.Position)
	local modeTag  = Settings.MovementMode == "Legal" and "LEGAL" or "FULL"
	local slapTag  = Runtime.slapState and (" | slap:" .. Runtime.slapState) or ""
	local phaseTag = (Settings.TV2_Enabled and Settings.TV2_Spacing) and (" | " .. Runtime.combatPhase) or ""
	local predTag  = Settings.PredictMovement and (" | pred:" .. tostring(Settings.PredictMode)) or ""
	StatusLabel.Text = ("[%s] %s | %.0fst | edge %.0f%s%s%s"):format(
		modeTag, target.Name, dist, edgeDist, slapTag, phaseTag, predTag
	)

	if Settings.DebugMode and DebugLabel then
		DebugLabel.Visible = true
		DebugLabel.Text = string.format(
			"phase:%s  slap:%s  vel:%.1f  edge:%.0f  forceReq:%s",
			Runtime.combatPhase,
			tostring(Runtime.slapState or "nil"),
			Runtime.smoothVel.Magnitude,
			edgeDist,
			tostring(Runtime.forceReequip)
		)
	else
		if DebugLabel then DebugLabel.Visible = false end
	end

	if dist <= Settings.SlapRadius + 10 then
		Runtime.LastReachedTime = tick()
	end
	if Settings.MovementMode ~= "Legal" and tick() - Runtime.LastReachedTime > Settings.StuckTimeout then
		Runtime.LastReachedTime = tick()
		hum.Health = 0
		return
	end

	-- Danger Mode
	if Settings.DangerMode and hum.Health <= Settings.DangerHP then
		ResetSlapState()
		if Settings.DangerType == "Backoff" then
			if Settings.MovementMode ~= "Legal" then
				local away = FlatUnit(root.Position - tRoot.Position)
				if away then MoveToTarget(root.Position + away * 28) end
			end
			FaceTarget(root, tRoot.Position)
			return
		elseif Settings.DangerType == "Give up" then
			return
		elseif Settings.DangerType == "Ahh" then
			hum.Health = 0
			return
		end
	end

	if MaybeSafeFromEdge(root) then
		if Settings.FaceTarget then
			FaceTarget(root, tRoot.Position)
		end
		StatusLabel.Text = ("[%s] EdgeSafe | %s | edge %.0f"):format(modeTag, target.Name, edgeDist)
		return
	end

	-- UnequipFar: solo desequipa el GLOVE si está lejos
	if Settings.UnequipFar and dist > Settings.UnequipRadius then
		if gloveEquipped and gloveTool and gloveTool.Parent == char then
			hum:UnequipTools()
		end
	end

	if Settings.Jumps and dist <= Settings.StrafeRadius then
		if math.random(1, 100) <= Settings.JumpChance then
			TryJump(hum, false)
		end
	end

	-- ===== MOVIMIENTO + ANTI MEGAROCK + SPACING v2 =====
	if Settings.MovementMode ~= "Legal" then
		local rock        = GetTargetRock(target)
		local wantBackOff = (Settings.BackOffAfterSlap and tick() < Runtime.justSlappedUntil)
			or Runtime.combatPhase == "backoff"

		if rock then
			StatusLabel.Text = ("[%s] Rodando Megarock | %s"):format(modeTag, target.Name)
			local rockPos  = RockPosition(rock)
			local rockToMe = root.Position - rockPos
			local flat     = FlatUnit(rockToMe) or Vector3.new(1, 0, 0)
			local safe     = Settings.MegarockSafeDist or 16
			-- Pequeño orbit lateral para evitar mejor
			local side = Vector3.new(-flat.Z, 0, flat.X)
			local bypass = flat * safe + side * (math.sin(tick() * 3.5) * 4)
			MoveToTarget(rockPos + bypass)
		elseif wantBackOff then
			local away = FlatUnit(root.Position - tRoot.Position)
			if away then
				-- Backoff más inteligente según velocidad relativa
				local extra = 0
				local relVel = tRoot.AssemblyLinearVelocity
				local approach = FlatUnit(root.Position - tRoot.Position)
				if approach then
					extra = math.clamp((relVel.X * approach.X + relVel.Z * approach.Z) * 0.15, 0, 4)
				end
				MoveToTarget(tRoot.Position + away * (Settings.BackOffDistance + extra))
			end
		elseif ShouldAvoidChase(root, tRoot) then
			local toCenter = FlatUnit(IslandMap_Main.center - root.Position)
			if toCenter then
				MoveToTarget(root.Position + toCenter * 17)
			end
		elseif Settings.TV2_Enabled and Settings.TV2_Spacing then
			Runtime.StrafeAngle      += dt * Settings.StrafeSpeed
			Runtime.orbitJitterPhase += dt * 2.2
			if Runtime.combatPhase == "chase" then
				MoveToTarget(GetChaseDestination(root, tRoot))
			elseif Runtime.combatPhase == "engage" or Runtime.combatPhase == "strike" then
				MoveToTarget(GetIdealFightPos(root, tRoot))
			end
		elseif Settings.StrafeEnabled and dist <= Settings.StrafeRadius then
			Runtime.StrafeAngle      += dt * Settings.StrafeSpeed
			Runtime.orbitJitterPhase += dt * 2.2
			if Settings.CenterBias or (Settings.TV2_Enabled and Settings.TV2_AngularApproach) then
				MoveToTarget(GetIdealFightPos(root, tRoot))
			else
				local orbit = Settings.OrbitDistance
				if Settings.OrbitJitter then
					orbit += math.sin(Runtime.orbitJitterPhase) * 1.8
				end
				local offset = Vector3.new(math.cos(Runtime.StrafeAngle) * orbit, 0, math.sin(Runtime.StrafeAngle) * orbit)
				local pred   = PredictTargetPos(root, tRoot)
				MoveToTarget(pred + offset)
			end
		elseif dist > Settings.EngageRadius then
			MoveToTarget(GetChaseDestination(root, tRoot))
		else
			Runtime.StrafeAngle += dt * Settings.StrafeSpeed
			if Settings.CenterBias or (Settings.TV2_Enabled and Settings.TV2_AngularApproach) then
				MoveToTarget(GetIdealFightPos(root, tRoot))
			else
				local offset = Vector3.new(math.cos(Runtime.StrafeAngle) * 3, 0, math.sin(Runtime.StrafeAngle) * 3)
				MoveToTarget(PredictTargetPos(root, tRoot) + offset)
			end
		end
	end

	-- ===== SLAP STATE MACHINE (directa + double) – robusta =====
	local strikeR = Settings.SlapRadius
	if Settings.TV2_Enabled and Settings.TV2_Spacing then
		strikeR = math.max(strikeR, Settings.TV2_StrikeDist or strikeR)
	end
	local inSlapRange = dist <= strikeR
	local tooClose    = dist < (Settings.MinSlapSeparation or 2.5)
	local currentTool = char:FindFirstChildOfClass("Tool")
	local canSlap     = inSlapRange and currentTool and not TargetIsCountering(target) and not tooClose

	if Settings.FaceTarget and dist <= Settings.ShiftlockRadius and Runtime.slapState == nil then
		local facePos = PredictTargetPos(root, tRoot)
		FaceTarget(root, facePos)
	end

	-- Timeout de seguridad global del slap SM
	if Runtime.slapState and tick() - Runtime.slapStateT > 0.55 then
		ResetSlapState()
	end

	if canSlap and Runtime.slapState == nil and tick() - Runtime.LastSlapActivate > (Settings.SlapCooldown or 0.15) then
		local useDirecta = WantSlapDirecta()
		Runtime.slapWantJump = Settings.JumpOnEngage and math.random(1, 100) <= 40
		if Settings.TV2_Enabled and Settings.TV2_DoubleSlap then
			Runtime.slapWantJump = true
		end
		if useDirecta then
			Runtime.cachedFlingDir = GetFlingDirection(root, tRoot)
			OrientToDirection(root, Runtime.cachedFlingDir)
			Runtime.faceLockUntil = tick() + (Settings.FlickLockTime or 0.24)
			Runtime.slapState  = "aim"
			Runtime.slapStateT = tick()
		else
			Runtime.slapState  = "fire"
			Runtime.slapStateT = tick()
		end
	end

	if Runtime.slapState == "aim" then
		if Runtime.cachedFlingDir then
			OrientToDirection(root, Runtime.cachedFlingDir)
		end
		Runtime.faceLockUntil = tick() + (Settings.FlickLockTime or 0.24)
		if tick() - Runtime.slapStateT >= (Settings.FlickAimTime or 0.05) then
			Runtime.slapState = "fire"
			Runtime.slapStateT = tick()
		end
	end

	if Runtime.slapState == "fire" then
		if WantSlapDirecta() and Runtime.cachedFlingDir then
			OrientToDirection(root, Runtime.cachedFlingDir)
			Runtime.faceLockUntil = tick() + (Settings.FlickLockTime or 0.24)
		end
		if currentTool then
			currentTool:Activate()
		end
		Runtime.LastSlapActivate = tick()
		Runtime.justSlappedUntil = tick() + 0.40

		if Runtime.slapWantJump or (Settings.TV2_Enabled and Settings.TV2_DoubleSlap) then
			TryJump(hum, true)
			if Settings.TV2_Enabled and Settings.TV2_DoubleSlap then
				Runtime.slapState  = "double"
				Runtime.slapStateT = tick()
			else
				ResetSlapState()
			end
		else
			ResetSlapState()
		end
	end

	if Runtime.slapState == "double" then
		if WantSlapDirecta() and Runtime.cachedFlingDir then
			OrientToDirection(root, Runtime.cachedFlingDir)
			Runtime.faceLockUntil = tick() + 0.12
		end
		-- Timeout de seguridad
		if tick() - Runtime.slapStateT > 0.42 then
			ResetSlapState()
		elseif tick() - Runtime.slapStateT >= (Settings.TV2_DoubleSlapDelay or 0.11) then
			if currentTool and dist <= strikeR + 5 then
				currentTool:Activate()
			end
			Runtime.LastSlapActivate = tick()
			Runtime.justSlappedUntil = tick() + 0.32
			ResetSlapState()
		end
	end

	-- Cancelaciones seguras
	if Runtime.slapState then
		if TargetIsCountering(target) then
			ResetSlapState()
		elseif not inSlapRange and tick() - Runtime.slapStateT > 0.36 then
			ResetSlapState()
		end
	end
end)


-- ============================================================================
-- [ABILITY LOOP]  Loop secundario de AutoAbility (más seguro)
-- ============================================================================

task.spawn(function()
	while true do
		task.wait(0.12)
		if not (Settings.Enabled and Settings.AutoAbility) then
			continue
		end
		local char = GetChar()
		local root = GetRoot(char)
		if not root then continue end

		-- Solo si el GLOVE está equipado
		local gloveName = GetGloveName()
		local tool = char and char:FindFirstChildOfClass("Tool")
		if not tool or not gloveName or tool.Name ~= gloveName then
			continue
		end

		local target = GetTarget()
		local dist   = 999
		if target then
			local tr = GetRoot(target.Character)
			if tr then
				dist = (root.Position - tr.Position).Magnitude
			end
		end

		local mode = Settings.AbilityMode
		if mode == "Combat" and dist <= 15 then
			TriggerAbility()
		elseif mode == "Defensive" then
			local hum = GetHum(char)
			if (dist > 12 and dist < 28) or (hum and hum.Health < Settings.DangerHP) then
				TriggerAbility()
			end
		elseif mode == "Instant" then
			TriggerAbility()
		elseif mode == "Combo" then
			local cur = GetSlaps()
			if cur > Runtime.LastSlapCount then
				task.wait(0.28)
				-- Re-verificar que el glove sigue equipado
				local stillTool = char:FindFirstChildOfClass("Tool")
				if stillTool and stillTool.Name == gloveName then
					TriggerAbility()
				end
			end
			Runtime.LastSlapCount = cur
		elseif mode == "Camping" and dist > 40 then
			TriggerAbility()
		end
		Runtime.LastSlapCount = GetSlaps()
	end
end)


print("[Slap Macro Tryhard v9.1 Safe Improved] Loaded | Predict reset + robust Slap SM + safer tools + Debug Mode")
