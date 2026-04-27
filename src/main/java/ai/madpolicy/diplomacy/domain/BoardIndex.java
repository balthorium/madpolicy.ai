package ai.madpolicy.diplomacy.domain;

import ai.madpolicy.diplomacy.model.BoardDocument;
import ai.madpolicy.diplomacy.model.BoardPosition;
import ai.madpolicy.diplomacy.model.BoardProvince;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BoardIndex {
    private final Map<String, String> provinceByPosition;
    private final Map<String, Set<String>> adjacencyByPosition;
    private final Map<String, BoardProvince> provinces;
    private final Map<String, BoardPosition> positions;

    public BoardIndex(BoardDocument board) {
        this.provinceByPosition = new HashMap<>();
        this.adjacencyByPosition = new HashMap<>();
        this.provinces = new HashMap<>(board.provinces());
        this.positions = new HashMap<>();

        for (Map.Entry<String, BoardProvince> entry : board.provinces().entrySet()) {
            String provinceId = entry.getKey();
            BoardProvince province = entry.getValue();
            for (BoardPosition position : province.positions()) {
                provinceByPosition.put(position.id(), provinceId);
                positions.put(position.id(), position);
                adjacencyByPosition.computeIfAbsent(position.id(), ignored -> new HashSet<>());
            }
        }

        for (var edge : board.adjacency()) {
            String left = edge.getFirst();
            String right = edge.getLast();
            adjacencyByPosition.computeIfAbsent(left, ignored -> new HashSet<>()).add(right);
            adjacencyByPosition.computeIfAbsent(right, ignored -> new HashSet<>()).add(left);
        }
    }

    public boolean areAdjacent(String left, String right) {
        return adjacencyByPosition.getOrDefault(left, Set.of()).contains(right);
    }

    public String provinceIdForPosition(String positionId) {
        return provinceByPosition.get(positionId);
    }

    public boolean provinceHasCoast(String provinceId) {
        BoardProvince province = provinces.get(provinceId);
        if (province == null) {
            return false;
        }
        return province.positions().stream().anyMatch(position -> "coast".equals(position.type()));
    }

    public boolean isLandPosition(String positionId) {
        BoardPosition position = positions.get(positionId);
        return position != null && "land".equals(position.type());
    }

    public boolean isSeaPosition(String positionId) {
        BoardPosition position = positions.get(positionId);
        return position != null && "sea".equals(position.type());
    }

    public boolean isCoastPosition(String positionId) {
        BoardPosition position = positions.get(positionId);
        return position != null && "coast".equals(position.type());
    }

    public Set<String> positionsForProvince(String provinceId) {
        BoardProvince province = provinces.get(provinceId);
        if (province == null) {
            return Set.of();
        }
        Set<String> ids = new HashSet<>();
        for (BoardPosition position : province.positions()) {
            ids.add(position.id());
        }
        return Collections.unmodifiableSet(ids);
    }

    public boolean canReachProvince(String fromPositionId, String provinceId) {
        for (String positionId : positionsForProvince(provinceId)) {
            if (areAdjacent(fromPositionId, positionId)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPossibleConvoyRoute(String fromPositionId, String toPositionId) {
        return convoyFleetCanParticipate(null, fromPositionId, toPositionId);
    }

    public boolean convoyFleetCanParticipate(String fleetPositionId, String fromPositionId, String toPositionId) {
        String fromProvince = provinceIdForPosition(fromPositionId);
        String toProvince = provinceIdForPosition(toPositionId);
        if (fromProvince == null || toProvince == null) {
            return false;
        }
        if (fromProvince.equals(toProvince)) {
            return false;
        }
        if (!isLandPosition(fromPositionId) || !isLandPosition(toPositionId)) {
            return false;
        }

        Set<String> startingSeas = touchingSeaPositions(fromProvince);
        Set<String> targetSeas = touchingSeaPositions(toProvince);
        if (startingSeas.isEmpty() || targetSeas.isEmpty()) {
            return false;
        }

        for (String start : startingSeas) {
            if (convoyPathExists(start, targetSeas, fleetPositionId, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    private boolean convoyPathExists(
        String current,
        Set<String> targetSeas,
        String requiredFleetPositionId,
        Set<String> visited
    ) {
        visited.add(current);
        boolean includesRequired = requiredFleetPositionId == null || visited.contains(requiredFleetPositionId);
        if (targetSeas.contains(current) && includesRequired) {
            return true;
        }

        for (String adjacent : adjacencyByPosition.getOrDefault(current, Set.of())) {
            if (!isSeaPosition(adjacent) || visited.contains(adjacent)) {
                continue;
            }
            if (convoyPathExists(adjacent, targetSeas, requiredFleetPositionId, visited)) {
                return true;
            }
        }

        visited.remove(current);
        return false;
    }

    private Set<String> touchingSeaPositions(String provinceId) {
        Set<String> touching = new HashSet<>();
        for (String provincePosition : positionsForProvince(provinceId)) {
            if (!isCoastPosition(provincePosition)) {
                continue;
            }
            for (String adjacent : adjacencyByPosition.getOrDefault(provincePosition, Set.of())) {
                if (isSeaPosition(adjacent)) {
                    touching.add(adjacent);
                }
            }
        }
        return touching;
    }
}
